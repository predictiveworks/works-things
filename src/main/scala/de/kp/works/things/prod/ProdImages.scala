package de.kp.works.things.prod

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

import de.kp.works.things.devices.DeviceRegistry
import de.kp.works.things.image.ImageMaker
import de.kp.works.things.logging.Logging
import de.kp.works.things.slack.SlackBot
import de.kp.works.things.tb.{TBAdmin, TBPoint}
import de.kp.works.things.ttn.{TTNAdmin, TTNDevice}
import org.knowm.xchart.BitmapEncoder.BitmapFormat
import org.knowm.xchart.{BitmapEncoder, XYChart}

import java.time.{Instant, LocalDateTime}
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import java.util.{Calendar, TimeZone}
import scala.collection.JavaConversions._
import scala.collection.mutable

class ProdCharts(numThreads:Int = 1) extends Logging {
  /**
   * The configured time interval the image maker
   * task is executed
   */
  private val interval:Long = 1000 * 60 * 60 * 12
  private val initialDelay = getInitialDelay

  private var executorService:ScheduledExecutorService = _
  private val imageMaker = new ProdImages()

  def start():Unit = {

    val worker = new Runnable {

      override def run(): Unit = {
        info(s"Prod Image Maker started.")
        imageMaker.buildImages()
      }
    }

    try {

      executorService = Executors.newScheduledThreadPool(numThreads)
      executorService.scheduleAtFixedRate(worker, initialDelay, interval, TimeUnit.MILLISECONDS)

    } catch {
      case t:Exception =>
        error(s"Prod Image Maker failed with: ${t.getLocalizedMessage}")
        stop()
    }

  }

  def stop():Unit = {

    executorService.shutdown()
    executorService.shutdownNow()

  }
  /**
   * Images from the respective sensor time series
   * are created at 6:00 (AM) and 18:00 (PM).
   *
   * This method computes the difference in milli
   * seconds from now to the next creation date
   */
  private def getInitialDelay:Long = {
    /*
     * Determine the current timestamp
     * in milliseconds
     */
    val cal = Calendar.getInstance
    val now = cal.getTimeInMillis

    val hour = cal.get(Calendar.HOUR_OF_DAY)
    /*
     * Normalize milli seconds, seconds
     * and minutes of the day
     */
    cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MINUTE, 0)

    if (hour < 6) {
      /*
       * The current timestamp is between
       * [00:00 and 06:00[, so we reset to
       * the 00:00 and add to 6 hours
       */
      cal.set(Calendar.HOUR_OF_DAY, 6)
      val next = cal.getTimeInMillis

      val duration = next - now
      return duration

    }

    if (hour >= 6 && hour < 18) {
      /*
       * The current timestamp is between
       * [06:00 and 18:00[, so we reset to
       * the 00:00 and add 18 hours
       */
      cal.set(Calendar.HOUR_OF_DAY, 18)
      val next = cal.getTimeInMillis

      val duration = next - now
      return duration

    } else if (hour > 18) {
      /*
       * The current timestamp is between
       * [18:00 and 23:59[, so we reset to
       * the 00:00 of the next day and add
       * 6 hours
       */
      cal.add(Calendar.DAY_OF_MONTH, 1)
      cal.set(Calendar.HOUR_OF_DAY, 6)
      val next = cal.getTimeInMillis

      val duration = next - now
      return duration

    }

    0L

  }

}

/**
 * This object is introduced to transform the last 24 hours
 * of measurements from the respective production stations
 * to Slack
 */
class ProdImages extends ImageMaker with Logging {

  private val rooms = ProdOptions.getRooms
  private val ttnDevices = getTTNDevices
  /*
   * The production image maker is related to
   * a Slack bot to send produced images to a
   * pre-defined Slack workspace
   */
  private val slackBot = new SlackBot()

  def buildImages():Unit = {
    /*
     * The current implementation generates a *.png (multiline)
     * image for each production room
     */
    rooms.foreach(room => {

      val tbAssetName = room.id
      /*
       * Retrieve attribute mapping of the specific room;
       * this encloses all sensors (attributes) tha refer
       * to all devices associated with the room asset
       */
      val lookup = ProdOptions.getMappingsByAsset(tbAssetName)
        .mappings.map(m =>
          (m.sensor, s"${m.label} (${m.units})")).toMap

      /*
       * The title is built from station and room
       * name
       */
      val roomTitle = s"${room.station}: ${room.name}"
      val roomCharts = mutable.ArrayBuffer.empty[XYChart]
      /*
       * Fill the respective room charts, which will
       * be joined into a single image
       */
      if (ttnDevices.contains(tbAssetName)) {

          ttnDevices(tbAssetName).foreach(ttnDevice => {
          /*
           * This request retrieves the values of the
           * last 24 hours
           */
          val tbAdmin = new TBAdmin()
          if (!tbAdmin.login())
            throw new Exception("Login to ThingsBoard failed.")
          /*
           * Retrieve the registry entry that refers
           * to the TTN device identifier
           */
          val deviceEntry = DeviceRegistry.getByTTNDeviceId(ttnDevice.device_id)
          if (deviceEntry.nonEmpty) {

            val tbDeviceId = deviceEntry.get.tbDeviceId
            /* ------------------------------
             *
             *     GET CLIENT ATTRIBUTES
             *
             */
            val tbKeys = tbAdmin.getTsKeys(tbDeviceId)
            Thread.sleep(10)
            /*
             * The timeseries contains all sensors (keys)
             * that are assigned to the specific device
             */
            val tbValues =
              getDeviceTs(tbAdmin, tbDeviceId, tbKeys)
            /*
             * Each `sensor` of the specific device is
             * visualized in its own chart
             */
            tbValues.foreach{case(sensor, values) =>
              val chartTitle = roomTitle + " - " + lookup(sensor)
              val chartValues = values.zipWithIndex
                .map{case(point, index) => (index.toDouble, point.ts, point.value)}

              val roomChart = buildChartTs(chartTitle, chartValues)
              roomCharts += roomChart
            }
          }
          /*
           * Requests to ThingsBoard are finished,
           * so logout and prepare for next login
           */
          tbAdmin.logout()

        })

        println(s"$tbAssetName: " + roomCharts.size)

        if (roomCharts.nonEmpty) {
          /*
          * Build the multi-chart image for all
          * sensors that refer to a certain room
          */
          val path = s"$getFolder$tbAssetName.png"
          BitmapEncoder.saveBitmap(roomCharts.toList, roomCharts.size, 1, path, BitmapFormat.PNG)
          /*
           * Finally send the current image to
           * the Hut & Stiel Slack channel
           */
          slackBot.upload(path, tbAssetName)

        }

      }

    })

  }

  override def formatTs(ts:Long):String = {

    val date = LocalDateTime
      .ofInstant(Instant.ofEpochMilli(ts), TimeZone.getDefault.toZoneId)

    val hour = date.getHour
    val minute = date.getMinute

    val hourStr = if (hour < 10) s"0$hour" else s"$hour"
    val minuteStr = if (minute < 10) s"0$minute" else s"$minute"

    val formatted = s"$hourStr:$minuteStr"
    formatted

  }
  /**
   * This method retrieves the timeseries of all
   * attributes (sensors) that refer to a certain
   * device
   */
  private def getDeviceTs(
    tbAdmin:TBAdmin, tbDeviceId:String, tbKeys:Seq[String]):Map[String, List[TBPoint]] = {
    /*
     * The timeseries contains all sensors (keys)
     * that are assigned to the specific device
     */
    val tbValues = try {

      val cal = Calendar.getInstance
      cal.add(Calendar.DAY_OF_MONTH, -1)

      val startTs = cal.getTime.getTime
      val endTs = System.currentTimeMillis()

      val tbParams = Map("startTs" -> startTs.toString, "endTs" -> endTs.toString)
      val tbTimeseries = tbAdmin.getTsHistorical(
        deviceId = tbDeviceId, keys = tbKeys, params = tbParams, limit = 10000)

      tbTimeseries

    } catch {
      case t: Throwable =>
        error(s"${t.getLocalizedMessage}")
        Map.empty[String, List[TBPoint]]
    }

    tbValues

  }

  private def getTTNDevices:Map[String, Seq[TTNDevice]] = {

    val ttnAdmin = new TTNAdmin()
    try {

      val ttnDevices = ttnAdmin.getDevices
      ttnDevices.groupBy(ttnDevice => ttnDevice.asset)

    } catch {
      case _:Throwable => Map.empty[String, Seq[TTNDevice]]
    }

  }

}
