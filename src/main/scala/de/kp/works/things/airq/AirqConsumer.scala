package de.kp.works.things.airq

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

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import de.kp.works.things.http.HttpConnect
import de.kp.works.things.tb._

import java.io.{BufferedWriter, File, FileWriter}
import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor

object AirqConsumer {

  private var instance:Option[AirqConsumer] = None

  def getInstance():AirqConsumer = {

    if (instance.isEmpty)
      instance = Some(new AirqConsumer)

    instance.get

  }

}

class AirqConsumer extends HttpConnect with AirqTransform {

  private val uuid = java.util.UUID.randomUUID.toString
  /**
   * Akka 2.6 provides a default materializer out of the box, i.e., for Scala
   * an implicit materializer is provided if there is an implicit ActorSystem
   * available. This avoids leaking materializers and simplifies most stream
   * use cases somewhat.
   */
  implicit val airqSystem: ActorSystem = ActorSystem(s"airq-system-$uuid")
  implicit lazy val airqContext: ExecutionContextExecutor = airqSystem.dispatcher

  implicit val airqMaterializer: ActorMaterializer = ActorMaterializer()

  private val apiUrl = AirqOptions.getBaseUrl
  private val interval = AirqOptions.getTimeInterval

  private val country = AirqOptions.getCountry
  private val pollutants = AirqOptions.getPollutants

  private val folder = AirqOptions.getFolder
  private val stations = AirqOptions.getStations
  /**
   * A flag that determines whether this consumer is retrieving
   * values from the [AirQuality] API for the configured weather
   * stations and their devices
   */
  private var consuming = true
  /**
   * The timestamp that determine when the last weather data where
   * retrieved from the AirQuality API
   */
  private var lastTs = 0L

  def start(): Unit = {

    while (consuming) {

      if (lastTs == 0L) {
        extractStations()
      }
      else {

        if (System.currentTimeMillis - lastTs < interval) {
          extractStations()
        }

      }
    }

  }

  def stop(): Unit = {
    consuming = false
    airqSystem.terminate()
  }
  /**
   * This method is responsible for extracting all
   * pollutant timeseries organized by pollutant
   * and respective qir quality station
   */
  def extractStations():Unit = {
    /*
     * Register the timestamp in milliseconds, the pollutant
     * data are retrieved from the [AirQuality] API
     */
    lastTs = System.currentTimeMillis

    val data = mutable.ArrayBuffer.empty[Seq[AirqPollutant]]
    /*
     * STEP #1: Download the pollutant data for
     * every configured pollutant, do filtering
     * and write the new data to the local file
     * system.
     *
     * Note, the Air Quality server holds values
     * for 48 hours and refreshes every 30 minutes
     */

    pollutants.foreach(pollutant =>
      data += download(pollutant))
    /*
     * STEP #2: The current implementation defines
     * an air quality station as a ThingsBoard asset
     * with pollutant devices connected.
     *
     * The current implementation specifies 60 different
     * devices that are organized as 17 assets
     */
    data.flatten
      .groupBy(airqPollutant => airqPollutant.stationId)
      .foreach { case (stationId, values) =>
        /*
         * The `stationId` refers to the asset level;
         * therefore, all sensors must be group by the
         * respective pollution type
         */
        val pollutantsByStation = values.groupBy(v => v.poll_type)
        /*
         * As a final step, the air quality timeseries
         * per pollution and station is extracted
         */
        pollutantsByStation.foreach { case (airqPoll, airqValues) =>
          /*
           * Determine device name: airqPoll is one of the
           * following values:
           *
           * ["CO", "NO", "NO2", "O3", "PM10", "PM2.5", "SO2"]
           */
          val replacement = ""
          val tbDeviceName = s"DEV.${airqPoll.toUpperCase}${stationId.replace("STA", replacement)}"
          /*
           * Transform `airqValues` into [TBRecord]s
           */
          val tbRecords = airqValues.map(airqValue => {

            val ts = airqValue.timestamp
            /*
             * The normalized pollutant is used as the telemetry
             * attribute key assigned to the ThingsBoard device
             */
            val name = airqPoll.replace(".", "").toLowerCase
            val value = airqValue.value

            val tbColumn = TBColumn(name, value)
            val tbRecord = TBRecord(ts, Seq(tbColumn))

            tbRecord

          })

          val tbTimeseries = TBTimeseries(tbRecords)
          /*
           * Build device specific actor and send TBJob
           * to this actor to publish time series records.
           *
           * This actor is automatically destroyed after
           * the provided TBJob is executed
           */
          val tbDeviceActor = airqSystem.actorOf(
            Props(new TBProducer()), s"$tbDeviceName-actor")

          val tbJob = TBJob(tbDeviceName, tbTimeseries)
          tbDeviceActor ! tbJob
        }
      }
  }

  /**
   * This is the main method that retrieves that actual
   * pollutant data for the provided and configured country
   */
  def download(pollutant:String):Seq[AirqPollutant] = {

    val key = s"${country}_$pollutant"
    val url = s"$apiUrl/$key.csv"

    val headers = Map.empty[String,String]
    val bytes = get(url, headers)
    /*
     * With respect to Austria, we must leverage the
     * charset below instead of UTF-8; note, the *.csv
     * download ships with a header line.
     *
     * The header is skipped as the format is expected
     * to be stable.
     */
    val lines = extractCsvBody(bytes, charset="ISO-8859-1").tail
    /*
     * Transform *.csv format into structured [AirqPollutant]
     * format and thereby convert values accordingly
     */
    val data = transform(lines, key, stations)

    val output = folder + s"$key.csv"
    val writer = new BufferedWriter(new FileWriter(new File(output)))

    data.foreach(item => writer.write(item.toString))
    writer.close()

    data

  }

}


