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

case class TBDeviceRequest(
  /*
   * The secret is used to restrict to authorized
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

  override def execute(request: HttpRequest): String = {

    val json = getBodyAsJson(request)
    if (json == null) {
      error(s"Request did not contain valid JSON.")
      return null

    }

    val req = mapper.readValue(json.toString, classOf[TBDeviceRequest])
    ""

  }
}

