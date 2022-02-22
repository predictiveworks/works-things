package de.kp.works.things.actors

/**
 * Copyright (c) 2019 - 2022 Dr. Krusche & Partner PartG. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * @author Stefan Krusche, Dr. Krusche & Partner PartG
 *
 */
import akka.actor.SupervisorStrategy._
import akka.actor.{Actor, ActorSystem, OneForOneStrategy}
import akka.http.scaladsl.coding.{Gzip, NoCoding}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.HttpEncodings
import akka.stream.ActorMaterializer
import akka.util.{ByteString, Timeout}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.gson._
import com.typesafe.config.Config
import de.kp.works.things.conf.ThingsConf
import de.kp.works.things.devices.RelationRegistry
import de.kp.works.things.logging.Logging
import de.kp.works.things.tb.{TBAdmin, TBOptions, TBPoint}
import org.thingsboard.server.common.data.Device

import java.util.Calendar
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.Try

case class DeviceValue(name:String, value:Double)
case class DeviceValues(device:String, values:Seq[DeviceValue])

abstract class BaseActor extends Actor with Logging {

  protected val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  protected val mobileCfg: Config = TBOptions.getMobileCfg
  protected val secret: String = mobileCfg.getString("secret")

  import BaseActor._
  /**
   * The actor system is implicitly accompanied by a materializer,
   * and this materializer is required to retrieve the ByteString
   */
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val actorCfg: Config = ThingsConf.getActorCfg

  implicit val timeout: Timeout = {
    val value = actorCfg.getInt("timeout")
    Timeout(value.seconds)
  }
  /**
   * Parameters to control the handling of failed child actors:
   * it is the number of retries within a certain time window.
   *
   * The supervisor strategy restarts a child up to 10 restarts
   * per minute. The child actor is stopped if the restart count
   * exceeds maxNrOfRetries during the withinTimeRange duration.
   */
  protected val maxRetries: Int = actorCfg.getInt("maxRetries")
  protected val timeRange: FiniteDuration = {
    val value = actorCfg.getInt("timeRange")
    value.minute
  }
  /**
   * Child actors are defined leveraging a RoundRobin pool with a
   * dynamic resizer. The boundaries of the resizer are defined
   * below
   */
  protected val lower: Int = actorCfg.getInt("lower")
  protected val upper: Int = actorCfg.getInt("upper")
  /**
   * The number of instances for the RoundRobin pool
   */
  protected val instances: Int = actorCfg.getInt("instances")
  /**
   * Each actor is the supervisor of its children, and as such each
   * actor defines fault handling supervisor strategy. This strategy
   * cannot be changed afterwards as it is an integral part of the
   * actor system’s structure.
   */
  override val supervisorStrategy: OneForOneStrategy =
  /*
   * The implemented supervisor strategy treats each child separately
   * (one-for-one). Alternatives are, e.g. all-for-one.
   *
   */
    OneForOneStrategy(maxNrOfRetries = maxRetries, withinTimeRange = timeRange) {
      case _: ArithmeticException      => Resume
      case _: NullPointerException     => Restart
      case _: IllegalArgumentException => Stop
      case _: Exception                => Escalate
    }

  override def receive: Receive = {

    case request:HttpRequest =>
      sender ! Response(Try({
        execute(request)
      })
      .recover {
          case t:Throwable =>
            /*
             * Send invalid message as response
             */
            error(t.getLocalizedMessage)
            throw new Exception(t.getLocalizedMessage)
        })

  }

  def execute(request:HttpRequest):String
  /**
   * This method is made fault resilient
   * and returns null in case of an error
   */
  def getBodyAsJson(request: HttpRequest):JsonElement = {

    try {

      /* BODY */

      /*
	     * Check whether the `Content-Encoding` header is set;
       * this is the case, if --logger_tls_compress=true.
       *
       * The encoding used in this case is `gzip`; other encoding
       * are not supported
       */
      val decoder = request.encoding match {
        case HttpEncodings.gzip => Gzip
        case _ => NoCoding
      }

      val decoded = decoder.decodeMessage(request)
      val source = decoded.entity.dataBytes

      /* Extract body as String from request entity */
      val future = source.runFold(ByteString(""))(_ ++ _)
      /*
       * We do not expect to retrieve large messages
       * and accept a blocking wait
       */
      val bytes = Await.result(future, timeout.duration)

      val body = bytes.decodeString("UTF-8")
      JsonParser.parseString(body)

    } catch {
      case t:Throwable =>
        error(t.getLocalizedMessage)
        null
    }

  }

  def buildEmptyDevices:String = {
    new JsonArray().toString
  }

  def buildEmptyDetail:String = {
    new JsonArray().toString
  }

  def buildEmptyStation:String = {

    val jsonObj = new JsonObject()
    jsonObj.addProperty("ts", System.currentTimeMillis)
    jsonObj.add("values", new JsonArray)

    jsonObj.toString

  }

  def buildEmptyPosition:String = {

    val jsonObj = new JsonObject()
    jsonObj.addProperty("ts", System.currentTimeMillis)

    jsonObj.add("latlon", new JsonArray)
    jsonObj.addProperty("step", -1)

    jsonObj.toString

  }

  def buildEmptyRoute:String = {
    new JsonArray().toString
  }

  def buildEmptyStations:String = {
    new JsonArray().toString
  }

  /**
   * This method retrieves the devices related
   * to the provided asset name; this is achieved
   * by reading the relation registry
   */
  def getDeviceIds(tbAssetName:String):List[String] = {

    val relationEntry = RelationRegistry.get(tbAssetName)
    if (relationEntry.isEmpty) return List.empty[String]

    relationEntry.get.tbToIds

  }

  def getDeviceLatest(tbAdmin:TBAdmin, tbAssetName:String):(Long, List[DeviceValues]) = {

    /* ------------------------------
     *
     *            GET DEVICES
     *
     * The request parameter `id` refers to the asset
     * name and is used to retrieve the respective
     * device identifiers from the relation registry
     */
    val tbDeviceIds = getDeviceIds(tbAssetName)

    val latestTs = mutable.ArrayBuffer.empty[Long]
    val latestVs = mutable.ArrayBuffer.empty[(String,String,Double)]

    tbDeviceIds.foreach(tbDeviceId => {
      /* ------------------------------
       *
       *     GET CLIENT ATTRIBUTES
       *
       */
      val tbKeys = tbAdmin.getTsKeys(tbDeviceId)
      Thread.sleep(10)

      if (tbKeys.nonEmpty) {
        /* ------------------------------
         *
         *     GET ATTRIBUTE VALUES
         *
         */
        val tbLatest = tbAdmin.getTsLatest(tbDeviceId, tbKeys)
        /*
         * This method flattens the results when a certain
         * sensor contains more than one attribute
         */
        tbLatest.foreach{case(attr, values) =>

          val point = values.head
          latestTs += point.ts
          /*
           * The device identifier is sent to the UI to
           * fasten the subsequent `historical` data
           * request
           */
          val latestValue = (tbDeviceId, attr, point.value)
          latestVs += latestValue

        }
      }
    })
    /*
     * Organize latest values with respect to the
     * device identifier
     */
    val latestValues = {

      if (latestVs.nonEmpty) {
        latestVs
          .groupBy{case(deviceId, _, _) => deviceId}
          .map{case(deviceId, data) =>
            val values = data.map{case(_, name, value) => DeviceValue(name, value)}
            DeviceValues(deviceId, values)
          }.toList

      } else
        List.empty[DeviceValues]
    }
    /*
     * Compute the average timestamp for all latest
     * device values
     */
    var timestamp = System.currentTimeMillis
    if (latestTs.nonEmpty)
      timestamp = latestTs.sum / latestTs.size

    (timestamp, latestValues)

  }
  /**
   * __MOD__ The interface has been extended to also support
   * user specific time interval for a certain time series.
   *
   * The default value = 30d and is automatically supported
   * by `getTsHistorical`. All other values, 1d, 3d, 10d must
   * be specified explicitly
   */
  def getDeviceTs(
     tbAdmin:TBAdmin, tbDeviceId:String,
     tbKeys:Seq[String], sensor:String, interval:String="30d"):List[TBPoint] = {

    val cal = Calendar.getInstance
    interval match {
      case "1d" =>
        cal.add(Calendar.DAY_OF_MONTH, -1)

      case "3d" =>
        cal.add(Calendar.DAY_OF_MONTH, -3)

      case "10d" =>
        cal.add(Calendar.DAY_OF_MONTH, -10)

      /*
       * The default interval is a month = 30d
       */
      case _ =>
        cal.add(Calendar.MONTH, -1)
    }

    val beginTs = cal.getTime.getTime
    val stopTs = System.currentTimeMillis()
    /*
     * The timeseries contains all sensors (keys)
     * that are assigned to the specific devices
     */
    val tbValues = try {

      val tbParams = Map("startTs" -> s"$beginTs", "endTs" -> s"$stopTs")
      val tbTimeseries = tbAdmin.getTsHistorical(
        deviceId = tbDeviceId, keys = tbKeys, params = tbParams, limit = 10000)

      tbTimeseries(sensor)

    } catch {
      case t: Throwable =>
        error(s"${t.getLocalizedMessage}")
        List.empty[TBPoint]
    }

    tbValues

  }

  def getDevices(tbAdmin:TBAdmin, tbAssetName:String):Seq[Device] = {

    /* ------------------------------
     *
     *            GET ASSET
     *
     * The request parameter `id` refers to the station
     * name and is used to retrieve the respective asset
     */
    val tbAsset = tbAdmin.getAssetByName(tbAssetName)
    Thread.sleep(10)

    val tbAssetId = tbAsset.getId.getId.toString
    /* ------------------------------
     *
     *          GET RELATIONS
     *
     * Retrieve all relations that refer to this asset
     * in order to access the respective sensors
     */
    val tbRelations = tbAdmin.getRelations(tbAssetId)
    Thread.sleep(10)
    /*
     * These relations are used to extract the identifiers
     * of those devices that are related to this asset
     */
    val toDeviceIds = tbRelations
      .map(tbRelation => tbRelation.getTo.getId.toString)
    /* ------------------------------
     *
     *          GET DEVICES
     *
     * In order to keep the number of requests to the
     * ThingsBoard server small, we retrieve all devices
     * here and filter on the client side
     */
    val tbDevices = tbAdmin.getDevices.filter(tbDevice => {
      toDeviceIds.contains(tbDevice.getId.getId.toString)
    })
    Thread.sleep(10)

    tbDevices

  }

}


object BaseActor {

  case class Response(status: Try[_])

}