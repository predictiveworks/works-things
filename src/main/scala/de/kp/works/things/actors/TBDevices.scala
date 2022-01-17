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
import com.google.gson.{JsonArray, JsonObject}
import de.kp.works.things.tb.TBQuery

case class TBDevicesRequest(
  /*
   * The secret used to restrict to authorized
   * requests
   */
  secret:String
)
/**
 * This actor supports the mobile access to the ThingsBoard
 * (HTTP) server and retrieves all devices managed by a certain
 * tenant and customer.
 */
class TBDevices extends BaseActor {

  private def query = new TBQuery()

  override def execute(request: HttpRequest): String = {

    val json = getBodyAsJson(request)
    if (json == null) {

      val now = new java.util.Date()
      log.error(s"[ERROR] ${now.toString} - Request did not contain valid JSON.")

      return null

    }

    val req = mapper.readValue(json.toString, classOf[TBDevicesRequest])

    if (req.secret.isEmpty || req.secret != query.getSecret) {

      val now = new java.util.Date()
      log.error(s"[ERROR] ${now.toString} - Unauthorized request detected.")

      return null

    }

    val devices = query.getDevices
    /*
     * The result contains a list of [Device] specifications,
     * that are not designed to be published; therefore, the
     * device data are reduced before sending back as response
     */
    val output = new JsonArray
    devices.foreach(device => {

      val deviceJson = new JsonObject
      deviceJson.addProperty("deviceId", device.getId.getId.toString)

      deviceJson.addProperty("deviceName", device.getName)
      deviceJson.addProperty("deviceType", device.getType)

      deviceJson.addProperty("deviceLabel", device.getLabel)
      /*
       * Retrieve device description from additional info
       */
      val additionalInfo = device.getAdditionalInfo
      var desc = ""
      try {
        val field = additionalInfo.get("description")
        desc = field.textValue()

      } catch {
        case _:Throwable =>
      }

      deviceJson.addProperty("deviceDesc", desc)
      output.add(deviceJson)

    })

    output.toString

  }

}
