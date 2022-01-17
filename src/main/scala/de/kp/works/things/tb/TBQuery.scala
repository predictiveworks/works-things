package de.kp.works.things.tb

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

import scala.collection.JavaConversions._
import scala.collection.mutable

case class TBPoint(ts:Long, value:Double)

class TBQuery extends TBClient {

  /**
   * This is the initial request to retrieve the time series
   * attributes of a certain device (identified by identifier)
   */
  def getTsKeys(deviceId:String):String = {

    if (authToken.isEmpty) {
      val now = new java.util.Date()
      throw new Exception(
        s"[ERROR] $now.toString - No access token found to access ThingsBoard.")
    }

    val deviceUrl = s"/api/plugins/telemetry/DEVICE/$deviceId/keys/timeseries"

    val endpoint = baseUrl + deviceUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = get(endpoint, header)
    val json = extractJsonBody(bytes)
    /*
     * Response format: ["gas", "temperature"]
     */
    val result = json.getAsJsonArray
      .map(k => k.getAsString).toSeq

    mapper.writeValueAsString(result)

  }
  /**
   * This request retrieves the latest time series values
   * of a certain device that refer to the provided keys
   */
  def getTsLatest(deviceId:String, keys:Seq[String]):String = {

    if (authToken.isEmpty) {
      val now = new java.util.Date()
      throw new Exception(
        s"[ERROR] $now.toString - No access token found to access ThingsBoard.")
    }

    val deviceUrl = s"/api/plugins/telemetry/DEVICE/$deviceId/values/timeseries?keys=${keys.mkString(",")}"

    val endpoint = baseUrl + deviceUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = get(endpoint, header)
    val json = extractJsonBody(bytes)
    /*
     * Response format:
     *
     * {
     *  "gas": [
     *    {
     *      "ts": 1479735870786,
     *      "value": "1"
     *    }
     *  ],
     *  "temperature": [
     *    {
     *      "ts": 1479735870786,
     *      "value": "3"
     *    }
     *  ]
     * }
     */
    val output = mutable.HashMap.empty[String, List[TBPoint]]

    val oldObj = json.getAsJsonObject
    oldObj.entrySet.foreach(entry => {

      val attr = entry.getKey
      val values = entry.getValue.getAsJsonArray
        .map(point => {

          val dp = point.getAsJsonObject

          val ts = dp.get("ts").getAsLong
          val value = dp.get("value").getAsString.toDouble

          TBPoint(ts, value)

        })
        .toList

      output += attr -> values

    })

    val result = output.toMap
    mapper.writeValueAsString(result)

  }
  /**
   * This request retrieves the time series of a certain device
   * that refer to the provided keys and params. The supported
   * parameters are described below:
   *
   * keys    - comma-separated list of telemetry keys to fetch.
   *
   * startTs - Unix timestamp that identifies the start of the
   *           interval in milliseconds.
   *
   * endTs   - Unix timestamp that identifies the end of the
   *           interval in milliseconds.
   *
   * interval - the aggregation interval, in milliseconds.
   * agg      - the aggregation function. One of MIN, MAX, AVG,
   *            SUM, COUNT, NONE.
   *
   * limit - the max amount of data points to return or intervals to process.
   */
  def getTsHistorical(deviceId:String, keys:Seq[String], params:Map[String, String], limit:Int = 1000):String = {

    if (authToken.isEmpty) {
      val now = new java.util.Date()
      throw new Exception(
        s"[ERROR] $now.toString - No access token found to access ThingsBoard.")
    }
    /*
     * STEP #1 Build base device url with provided keys and limit
     */
    var deviceUrl = s"/api/plugins/telemetry/DEVICE/$deviceId/values/timeseries?keys=${keys.mkString(",")}&limit=$limit"
    /*
     * STEP #2: Append optional parameters to device url
     *
     * Note, if `startTs` and `endTs` is not provided, ThingsBoard
     * will return the latest values, as the respective url is the
     * same one. Therefore, `startTs` and `endTs` are required
     */
    val startTs = params.getOrElse("startTs", "0").toLong
    deviceUrl = deviceUrl + s"&startTs=$startTs"

    val endTs = params.getOrElse("endTs", s"${System.currentTimeMillis()}").toLong
    deviceUrl = deviceUrl + s"&endTs=$endTs"
    /*
     * In use cases where the `startTs` parameter is set to `0`,
     * interval and aggregation parameters are ignored, as this
     * results in a bad request
     */
    if (startTs > 0) {

      if (params.contains("interval")) {
        val interval = params("interval").toLong
        deviceUrl = deviceUrl + s"&interval=$interval"
      }
      /*
       * __KUP__
       *
       * The aggregation function `agg` is described in the REST API
       * documentation (time-series data).
       *
       * Leveraging `aggregation` however, requires to provide start
       * and end timestamps that cover a valid data regions. Otherwise,
       * the ThingsBoard server responds with a bad request:
       */
      val allowedAggs = Array("MIN", "MAX", "AVG", "SUM", "COUNT", "NONE")
      if (params.contains("agg")) {

        val agg = params("agg")
        if (allowedAggs.contains(agg))
          deviceUrl = deviceUrl + s"&agg=$agg"

      }

    }

    val endpoint = baseUrl + deviceUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = get(endpoint, header)
    val json = extractJsonBody(bytes)
    /*
     * Response format:
     *
     * {
     *  "gas": [
     *    {
     *      "ts": 1479735870786,
     *      "value": "1"
     *    },
     *    {
     *      "ts": 1479735871857,
     *      "value": "2"
     *    }
     *  ],
     *  "temperature": [
     *    {
     *      "ts": 1479735870786,
     *      "value": "3"
     *    },
     *    {
     *      "ts": 1479735871857,
     *      "value": "4"
     *    }
     *  ]
     * }
     *
     * The response time series is returned in
     * descending order and are ordered the other
     * way round before getting returned
     */
    val output = mutable.HashMap.empty[String, List[TBPoint]]

    val oldObj = json.getAsJsonObject
    oldObj.entrySet.foreach(entry => {

      val attr = entry.getKey
      val values = entry.getValue.getAsJsonArray
        .map(point => {

          val dp = point.getAsJsonObject

          val ts = dp.get("ts").getAsLong
          val value = dp.get("value").getAsString.toDouble

          TBPoint(ts, value)

        })
        .toList
        .sortBy(p => p.ts)

      output += attr -> values

    })

    val result = output.toMap
    mapper.writeValueAsString(result)

  }

}
