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

import de.kp.works.things.http.HttpConnect

import java.io.{BufferedWriter, File, FileWriter}
import java.text.SimpleDateFormat
import scala.collection.mutable

/**
 * An [AirqPoint] defines a certain time-value
 * data point of a specific pollutant
 */
case class AirqPoint(ts:Long, value:Double)

case class AirqStation(id:String, name:String, lon:Double, lat:Double)

class AirqConsumer extends HttpConnect with AirqTransform {

  private val apiUrl = AirqOptions.getBaseUrl
  private val apiInterval = 1000 * 60 * 30

  private val country = AirqOptions.getCountry
  private val pollutants = AirqOptions.getPollutants

  private val folder = AirqOptions.getFolder
  private val stations = AirqOptions.getStations

  def start(): Unit = {}

  def stop(): Unit = {}

  def extractPollutants():Unit = {

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
     * an Air Quality station as a gateway device
     * with pollutant devices connected.
     *
     * This approach reduces the amount of requests
     * drastically, as we publish all pollutants for
     * every station once
     */
    val pollutantsByStations = data.flatten
      .groupBy(airqPollutant => airqPollutant.stationId)
      .map{case(stationId, values) =>
        /*
         * The `stationId` refers to the gateway level;
         * therefore, all sensors must be group by the
         * respective pollution type
         */
        val pollutantsByStation = values.groupBy(v => v.poll_type)
        /*
         * As a final step, the Air Quality timeseries
         * per pollution and station is extracted
         */
        val pollutantsTimeSeries = pollutantsByStation.map{case(airqPoll, airqValues) => {

          val airqTs = airqValues
            .map(airqV => AirqPoint(airqV.timestamp, airqV.value))
            .sortBy(airqPoint => airqPoint.ts)

          (airqPoll, airqTs)

        }}

        (stationId, pollutantsTimeSeries)
      }

    println(pollutantsByStations)

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


