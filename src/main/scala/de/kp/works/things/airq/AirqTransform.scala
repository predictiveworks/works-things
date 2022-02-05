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

import java.text.SimpleDateFormat
import java.util.Calendar
import scala.collection.mutable

case class AirqPollutant(
  /*
   * `timestamp` is the latest (inserted or updated)
   * timestamp that refers to the numeric value
   */
  timestamp:Long,
  /*
   * `type` specifies the pollutant, i.e. CO, NO or
   * other pollutant types
   */
  poll_type: String,
  /*
   * The geospatial coordinates of the sampling point;
   * if the respective sensors of a certain station
   * are distributed, this is the station coordinate.
   */
  longitude: Double,
  latitude: Double,
  /*
   * The local unique identifier of the assigned station
   */
  stationId: String,
  stationName: String,
  /*
   * Time values that refer to the measurement process
   */
  beginTime: Long,
  endTime: Long,
  insertTime: Long,
  updateTime: Long,
  /*
   * The numeric values of the pollutant
   */
  value: Double,
  units: String
) {

  override def toString:String = {
    val values = Seq(
      timestamp,
      poll_type,
      longitude,
      latitude,
      stationId,
      stationName,
      beginTime,
      endTime,
      insertTime,
      updateTime,
      value,
      units
    )

    values.mkString(",")
  }

}

trait AirqTransform {

  protected val dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
  /*
   * This internal cache registers the most recent values
   * per pollutant (covering multiple stations).
   *
   * These values are retrieved from the filesystem and
   * will be stored during each download request.
   */
  protected val latestValues = mutable.HashMap.empty[String, Seq[AirqPollutant]]

  def transform(
    lines:List[String], key:String, stations:List[AirqStation], file:String):Seq[AirqPollutant] = {

    /*
     * STEP #1: The latest value registered on the
     * filesystem is loaded and used to fill the
     * latest (in-memory) values
     */
    loadLatest(key, file)
    /*
     * STEP #2: Transform `lines` into pollutants
     * and restrict to those that have not been
     * registered already
     */
    val stationIds = stations.map(s => s.id)
    /*
     * The air quality files have the following format:
     *
     * [0] : network_countrycode
     * [1] : network_localid
     * [2] : network_name
     * [3] : network_namespace
     * [4] : network_timezone
     * [5] : pollutant
     * [6] : samplingpoint_localid
     * [7] : samplingpoint_namespace
     * [8] : samplingpoint_x           // longitude
     * [9] : samplingpoint_y           // latitude
     * [10]: coordsys
     * [11]: station_code
     * [12]: station_localid
     * [13]: station_name
     * [14]: station_namespace
     * [15]: value_datetime_begin
     * [16]: value_datetime_end
     * [17]: value_datetime_inserted
     * [18]: value_datetime_updated
     * [19]: value_numeric
     * [20]: value_validity
     * [21]: value_verification,StringType,true
     * [22]: station_altitude
     * [23]: value_unit
     */
    val filtered = lines.filter(line => {
      val tokens = line.split(",")
      /*
       * Ensure that the data line refers to one
       * of the configured local station ids
       */
      val station_localid = tokens(12)
      if (!stationIds.contains(station_localid)) false
      else {
        /*
         * Ensure that the respective numeric
         * value is not empty or null
         */
        val numeric_value = tokens(19)
        if (numeric_value.isEmpty || numeric_value.toLowerCase == "null") false
        else true
      }
    })
    /*
     * As a next step the lines are reduced to those
     * that are relevant for ThingsBoard
     */
    val currentValues = filtered.map(line => {

      val tokens = line.split(",")

      val datetime_inserted = datetimeToLong(tokens(17))
      val datetime_updated  = datetimeToLong(tokens(18))

      val timestamp =
        if (datetime_updated == 0L) datetime_inserted else datetime_updated

      AirqPollutant(
        /* value_timestamp (enriched) */
        timestamp   = timestamp,
        /* pollutant */
        poll_type   = tokens(5),
        /* samplingpoint_x */
        longitude   = tokens(8).toDouble,
        /* samplingpoint_y */
        latitude    = tokens(9).toDouble,
        /* station_localid */
        stationId   = tokens(12),
        /* station_name */
        stationName = tokens(13),
        /* value_datetime_begin */
        beginTime   = datetimeToLong(tokens(15)),
        /* value_datetime_end */
        endTime     = datetimeToLong(tokens(16)),
        /* value_datetime_inserted */
        insertTime  = datetime_inserted,
        /* value_datetime_updated */
        updateTime  = datetime_updated,
        /* value_numeric */
        value       = tokens(19).toDouble,
        /* value_unit */
        units       = tokens(23)
      )

    })
    /*
     * The datasets of two subsequent download
     * requests overlap; to avoid duplicate keys
     * within ThingsBoard's postgres database,
     * the values must be filtered
     */
    val data = currentValues
      .groupBy(poll => poll.stationId)
      .flatMap{case(stationId, values) =>
        if (latestValues.contains(key)) {
          /*
           * Retrieve all recent values for the
           * respective pollutant (key)
           */
          val latestStation = latestValues(key)
            .filter(poll => poll.stationId == stationId)

          if (latestStation.isEmpty) values
          else {

            val latestTs = latestStation.head.timestamp
            values.filter(v => v.timestamp > latestTs)

          }

        } else {
          values
        }
      }.toList

    data

  }

  private def datetimeToLong(value:String):Long = {

    try {

      val splits = value.split("\\+")
      val utc = splits(0)

      val date = dateFormat.parse(utc)

      val cal = Calendar.getInstance()
      cal.setTime(date)

      val hours = splits(1)
        .split(":").head.toInt

      cal.add(Calendar.HOUR_OF_DAY, hours)
      cal.getTimeInMillis

    } catch {
      case _:Throwable => 0L
    }

  }

  private def loadLatest(key:String, path:String):Unit = {

    val file = new java.io.File(path)
    if (!file.exists()) return

    val source = scala.io.Source.fromFile(file)

    val lines = source.getLines().toList
    val pollutants = lines.map(line => {

      val Array(
      timestamp,
      poll_type,
      longitude,
      latitude,
      stationId,
      stationName,
      beginTime,
      endTime,
      insertTime,
      updateTime,
      value,
      units)  = line.split(",")

      AirqPollutant(
        timestamp.toLong,
        poll_type,
        longitude.toDouble,
        latitude.toDouble,
        stationId,
        stationName,
        beginTime.toLong,
        endTime.toLong,
        insertTime.toLong,
        updateTime.toLong,
        value.toDouble,
        units)

    })

    latestValues += key -> pollutants
    source.close

  }
}
