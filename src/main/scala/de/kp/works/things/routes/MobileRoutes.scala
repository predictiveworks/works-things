package de.kp.works.things.routes

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

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import de.kp.works.things.actors.BaseActor.Response

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.{Failure, Success}

object MobileRoutes {

  val AIRQ_DETAIL_ACTOR   = "airq_detail_actor"
  val AIRQ_STATION_ACTOR  = "airq_station_actor"
  val AIRQ_STATIONS_ACTOR = "airq_stations_actor"

  val OWEA_DETAIL_ACTOR   = "owea_detail_actor"
  val OWEA_STATION_ACTOR  = "owea_station_actor"
  val OWEA_STATIONS_ACTOR = "owea_stations_actor"

  val PROD_DETAIL_ACTOR   = "prod_detail_actor"
  val PROD_STATION_ACTOR  = "prod_station_actor"
  val PROD_STATIONS_ACTOR = "prod_stations_actor"

  val TB_DEVICE_ACTOR  = "tb_device_actor"
  val TB_DEVICES_ACTOR = "tb_devices_actor"

  val TTN_DEVICES_ACTOR = "ttn_devices_actor"
  val TTN_UPDATE_ACTOR = "ttn_update_actor"

}

class MobileRoutes(actors:Map[String, ActorRef])(implicit system: ActorSystem) {

  implicit lazy val context: ExecutionContextExecutor = system.dispatcher
  /**
   * Common timeout for all Akka connections
   */
  val duration: FiniteDuration = 15.seconds
  implicit val timeout: Timeout = Timeout(duration)

  import MobileRoutes._

  def getRoutes:Route = {
    getAirqDetail ~
    getAirqStation ~
    getAirqStations ~
    getOweaDetail ~
    getOweaStation ~
    getOweaStations ~
    getProdDetail ~
    getProdStation ~
    getProdStations ~
    getTBDevice ~
    getTBDevices ~
    getTTNDevices
  }

  /*
   * Routes that support mobile REST request to
   * air quality related devices and stations
   */
  private def getAirqDetail:Route = routePost("things/v1/mobile/airq/detail", actors(AIRQ_DETAIL_ACTOR))

  private def getAirqStation:Route = routePost("things/v1/mobile/airq/station", actors(AIRQ_STATION_ACTOR))

  private def getAirqStations:Route = routePost("things/v1/mobile/airq/stations", actors(AIRQ_STATIONS_ACTOR))
  /*
   * Routes that support mobile REST request to
   * weather related devices and stations
   */
  private def getOweaDetail:Route = routePost("things/v1/mobile/owea/detail", actors(OWEA_DETAIL_ACTOR))

  private def getOweaStation:Route = routePost("things/v1/mobile/owea/station", actors(OWEA_STATION_ACTOR))

  private def getOweaStations:Route = routePost("things/v1/mobile/owea/stations", actors(OWEA_STATIONS_ACTOR))
  /*
   * Routes that support mobile REST request to
   * production related devices and stations
   */
  private def getProdDetail:Route = routePost("things/v1/mobile/prod/detail", actors(PROD_DETAIL_ACTOR))

  private def getProdStation:Route = routePost("things/v1/mobile/prod/station", actors(PROD_STATION_ACTOR))

  private def getProdStations:Route = routePost("things/v1/mobile/prod/stations", actors(PROD_STATIONS_ACTOR))
  /*
   * Routes that support mobile REST request to
   * ThingsBoard related devices and stations
   */
  private def getTBDevice:Route = routePost("things/v1/mobile/tb/device", actors(TB_DEVICE_ACTOR))

  private def getTBDevices:Route = routePost("things/v1/mobile/tb/devices", actors(TB_DEVICES_ACTOR))
  /*
   * Routes that support mobile REST request to
   * The ThingsNetwork related devices and stations
   */
  private def getTTNDevices:Route = routePost("things/v1/mobile/ttn/devices", actors(TTN_DEVICES_ACTOR))

  /*******************************
   *
   * HELPER METHODS
   *
   */
  private def routePost(url:String, actor:ActorRef):Route = {
    val matcher = separateOnSlashes(url)
    path(matcher) {
      post {
        /*
         * The client sends sporadic [HttpEntity.Default]
         * requests; the [BaseActor] is not able to extract
         * the respective JSON body from.
         *
         * As a workaround, the (small) request is made
         * explicitly strict
         */
        toStrictEntity(duration) {
          extract(actor)
        }
      }
    }
  }

  private def extract(actor:ActorRef) = {
    extractRequest { request =>
      complete {
        /*
         * The Http(s) request is sent to the respective
         * actor and the actor' response is sent to the
         * requester as response.
         */
        val future = actor ? request
        Await.result(future, timeout.duration) match {
          case Response(Failure(e)) =>
            val message = e.getMessage
            jsonResponse(message)
          case Response(Success(answer)) =>
            val message = answer.asInstanceOf[String]
            jsonResponse(message)
        }
      }
    }
  }

  private def jsonResponse(message:String) = {

    HttpResponse(
      status=StatusCodes.OK,
      entity = ByteString(message),
      protocol = HttpProtocols.`HTTP/1.1`)

  }

}
