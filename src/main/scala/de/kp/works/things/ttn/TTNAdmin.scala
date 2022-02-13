package de.kp.works.things.ttn

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
import scala.collection.JavaConversions._

case class TTNDevice(
  /*
   * The unique application identifier this
   * device refers to within The ThingsNetwork
   */
  application_id: String,
  /*
   * The unique device identifier assigned to
   * this device by The ThingsNetwork
   */
  device_id: String,
  dev_eui: String,
  join_eui: String,
  /*
   * The Mqtt topic required to access this device
   */
  mqtt_topic: String,
  /*
   * The `name` of this device as available in
   * the device registry of The ThingsNetwork
   */
  name: String,
  /*
   * The `description` of this device as available
   * in the device registry of The ThingsNetwork
   */
  description: String,
  /*
   * The geo spatial coordinates of this device as
   * available in the device registry of The ThingsNetwork
   */
  latitude: Double,
  longitude: Double,
  altitude: Double,
  /*
   * The asset identifier, this TTN is connected to.
   * It is used as a back reference, and refers that
   * ThingsBoard asset, the device is connected to.
   *
   * In case of the production use case, this is the
   * respective room identifier
   */
  asset:String
)

object TTNAdmin {

  def main(args:Array[String]):Unit = {

    val admin = new TTNAdmin
    println(admin.getDevices)

    System.exit(0)
  }
}

class TTNAdmin extends HttpConnect {

  private val adminCfg = TTNOptions.getAdminCfg

  private val appId = adminCfg.getString("appId")
  private val baseUrl = adminCfg.getString("serverUrl")

  private val authToken = TTNOptions.getAuthToken
  /*
   * The assigned (additional) field_mask is mandatory,
   * if addition information beyond identifiers must
   * be retrieved:
   *
   * https://www.thethingsindustries.com/docs/reference/api/
   */
  private val devicesUrl = s"/api/v3/applications/$appId/devices?field_mask=name,description,locations,attributes"

  def getDevices:Seq[TTNDevice] = {

    val endpoint = baseUrl + devicesUrl
    val header = Map(
      "Authorization"-> s"Bearer $authToken", "User-Agent" -> "predictiveworks")

    val bytes = get(endpoint, header)
    val json = extractJsonBody(bytes)
    /*
     * Response format:
     *
     * {"end_devices":[
     *    {"ids": {
     *      "device_id":"eui-70b3d57ed004b31f",
     *      "application_ids":{
     *        "application_id":"hutundstiel"
     *      },
     *      "dev_eui":"70B3D57ED004B31F",
     *      "join_eui":"0000000000000000"
     *    },
     *    "created_at":"2022-01-13T09:42:03.376Z",
     *    "updated_at":"2022-01-13T09:42:03.376Z",
     *    "name":"Dragino DHT65",
     *    "description":"This is a Dragino humidity & temperature sensor used to monitor oyster mushroom production.",
     *    "locations":{
     *      "user":{
     *        "latitude":48.16206944391096,
     *        "longitude":16.525613888888902,
     *        "source":"SOURCE_REGISTRY"
     *      }
     *    },
     *    "attributes": {
     *      "asset": "ROOM.INCU.HUTS.KSTAF"
     *    }
     *  }
     * ]}
     */

    val devices = json.getAsJsonObject.get("end_devices").getAsJsonArray
    devices
      .map(device => {

      val deviceObj = device.getAsJsonObject
      /*
       * STEP #1: Extract identifiers that are assigned to
       * the respective end device
       */
      val idsObj = deviceObj.get("ids").getAsJsonObject
      val device_id = idsObj.get("device_id").getAsString

      val dev_eui  = idsObj.get("dev_eui").getAsString
      val join_eui = idsObj.get("join_eui").getAsString

      val application_id = idsObj.get("application_ids").getAsJsonObject
                            .get("application_id").getAsString
      /*
       * STEP #2: Specify the MQTT topic to access the
       * respective device in The ThingsNetwork
       */
      val mqttTopic = s"v3/$application_id@ttn/devices/$device_id/up"
      /*
       * STEP #3: Extract metadata (general settings tab)
       * for each device; note, fields `name`, `description`
       * and `locations` are added as field mask to the
       * TTN request
       */
      val name = {
        val v = deviceObj.get("name")
        if (v == null) {
          /*
            * The name of the TTN device must exist:
            * This parameter is used as the relation
            * between TTN and TB devices during creation
            */
          s"TTN-${java.util.UUID.randomUUID.toString}"

        } else v.getAsString
      }

      val description = {
        val v = deviceObj.get("description")
        if (v == null) "" else v.getAsString
      }

      val (latitude, longitude, altitude) = {
        val v = deviceObj.get("locations")
        if (v == null) (0.0, 0.0, 0.0)
        else {
          val user = v.getAsJsonObject.get("user")
          if (user == null) (0.0, 0.0, 0.0)
          else {
            val userObj = user.getAsJsonObject
            val lat = {
              val v = userObj.get("latitude")
              if (v == null) 0D else v.getAsDouble
            }

            val lon = {
              val v = userObj.get("longitude")
              if (v == null) 0D else v.getAsDouble
            }

            val alt = {
              val v = userObj.get("altitude")
              if (v == null) 0D else v.getAsDouble
            }

            (lat, lon, alt)
          }
        }
      }

      val asset = {
        val v = deviceObj.get("attributes")
        if (v == null) ""
        else {
          val assetObj = v.getAsJsonObject.get("asset")
          if (assetObj == null) ""
          else
            assetObj.getAsString
        }
      }

      TTNDevice(
        application_id = application_id,
        device_id      = device_id,
        dev_eui        = dev_eui,
        join_eui       = join_eui,
        mqtt_topic     = mqttTopic,
        name           = name,
        description    = description,
        latitude       = latitude,
        longitude      = longitude,
        altitude       = altitude,
        asset          = asset
      )

    }).toSeq

  }
}
