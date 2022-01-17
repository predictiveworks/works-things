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

import akka.http.scaladsl.model.HttpRequest
import de.kp.works.things.tb.TBQuery

case class TBDeviceRequest(
  /*
   * The secret used to restrict to authorized
   * requests
   */
  secret:String,
  /*
   * The unique device identifier
   */
  deviceId:String,
  /*
   * The request method; supported values are:
   *
   * - attributes
   * - latest_values
   * - historical_values
   */
  method:String,
  /*
   * The attributes for which latest values or
   * (aggregated) historical data are requested
   */
  attrs: Seq[String] = Seq.empty[String],
  /*
   * The request parameters that refer to the
   * `historical_values` request
   */
  params:Map[String,String] = Map.empty[String,String],
  /*
   * The maximum number of data points to return
   */
  limit:Int = 1000
)
/**
 * This actor supports the mobile access to the ThingsBoard
 * (HTTP) server and retrieves device specific data, including
 * latest values and historical data.
 */
class TBDevice extends BaseActor {

  private def query = new TBQuery()

  override def execute(request: HttpRequest): String = {

    val json = getBodyAsJson(request)
    if (json == null) {

      val now = new java.util.Date()
      log.error(s"[ERROR] ${now.toString} - Request did not contain valid JSON.")

      return null

    }

    val req = mapper.readValue(json.toString, classOf[TBDeviceRequest])

    if (req.secret.isEmpty || req.secret != query.getSecret) {

      val now = new java.util.Date()
      log.error(s"[ERROR] ${now.toString} - Unauthorized request detected.")

      return null

    }

    if (req.deviceId.isEmpty) {

      val now = new java.util.Date()
      log.error(s"[ERROR] ${now.toString} - No device identifier provided.")

      return null

    }

    req.method match {
      case "attributes" =>
        query.getTsKeys(req.deviceId)

      case "latest_values" =>
        if (req.attrs.isEmpty) {

          val now = new java.util.Date()
          log.error(s"[ERROR] ${now.toString} - No attributes provided.")

          null

        }
        else
          query.getTsLatest(req.deviceId, req.attrs)

      case "historical_values" =>
        if (req.attrs.isEmpty) {

          val now = new java.util.Date()
          log.error(s"[ERROR] ${now.toString} - No attributes provided.")

          null

        }
        else
          query.getTsHistorical(req.deviceId, req.attrs, req.params, req.limit)

      case _ =>

        val now = new java.util.Date()
        log.error(s"[ERROR] ${now.toString} - Unknown request method `${req.method}` detected.")

        null

    }

  }
}

