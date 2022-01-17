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

import com.google.gson.{JsonArray, JsonElement, JsonNull}
import org.thingsboard.server.common.data.Device

import scala.collection.JavaConversions._

object TBAdmin {

  val TTN_DEVICE_ID  = "ttn_device_id"
  val TTN_MQTT_TOPIC = "ttn_mqtt_topic"

}

class TBAdmin extends TBClient {

  private val deviceUrl = "/api/device"
  private val attributesUrl = "/api/plugins/telemetry/DEVICE/"

  private val customerId = adminCfg.getString("customerId")

  def getCustomerId: String = customerId

  def createDevice(device:Device):JsonElement = {

    if (authToken.isEmpty) {
      val now = new java.util.Date()
      throw new Exception(
        s"[ERROR] $now.toString - No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + deviceUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = post(endpoint, header, mapper.writeValueAsString(device))

    val json = extractJsonBody(bytes)
    json

  }
  /**
   * This method retrieves the ThingsBoard device access token
   * that was automatically created by ThingsBoard; in this case,
   * the credentials identifier represents the access token and
   * the credentials value is `null`.
   *
   * The access token is part of the ThingsBoard MQTT topic, that
   * is used to published device telemetry data.
   */
  def getDeviceToken(deviceId:String):String = {

    if (authToken.isEmpty) {
      val now = new java.util.Date()
      throw new Exception(
        s"[ERROR] $now.toString - No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + deviceUrl + s"/$deviceId/credentials"
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = get(endpoint, header)
    val json = extractJsonBody(bytes)
    /*
     * {
     *    "id":{
     *      "id":"f80261a0-76d5-11ec-9849-35fe48003809"
     *    },
     *    "createdTime":1642342190266,
     *    "deviceId":{
     *      "entityType":"DEVICE",
     *      "id":"f8017740-76d5-11ec-9849-35fe48003809"
     *    },
     *    "credentialsType":"ACCESS_TOKEN",
     *    "credentialsId":"qnvtBm5SW24rUCXU207z",
     *    "credentialsValue":null
     * }
     */
    val credsObj = json.getAsJsonObject

    val credsType = credsObj.get("credentialsType").getAsString
    if (credsType != "ACCESS_TOKEN") return null

    val deviceToken = credsObj.get("credentialsId").getAsString
    deviceToken

  }

  def createServerAttributes(deviceId:String, payload:String):JsonElement = {

    if (authToken.isEmpty) {
      val now = new java.util.Date()
      throw new Exception(
        s"[ERROR] $now.toString - No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + attributesUrl + s"$deviceId/SERVER_SCOPE"
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = post(endpoint, header, payload)
    val json = extractJsonBody(bytes)
    json

  }

  def getServerAttribute(deviceId:String, name:String):JsonElement = {

    val attributes = getServerAttributes(deviceId)
      .filter(attr => {

        val attrObj = attr.getAsJsonObject
        val key = attrObj.get("key").getAsString

        if (key == name) true else false

      }).toSeq

    if (attributes.isEmpty) return JsonNull.INSTANCE

    val attrObj = attributes.head.getAsJsonObject
    attrObj.get("value")

  }

  def getServerAttributes(deviceId:String):JsonArray = {

    if (authToken.isEmpty) {
      val now = new java.util.Date()
      throw new Exception(
        s"[ERROR] $now.toString - No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + attributesUrl + s"$deviceId/values/attributes/SERVER_SCOPE"
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = get(endpoint, header)
    val json = extractJsonBody(bytes)
    /*
     * Response format:
     *
     * [
     *  {
     *    "lastUpdateTs":1641990172802,
     *    "key":"latitude",
     *    "value":48.16206944391096
     *  },
     *  {
     *    "lastUpdateTs":1641990212558,
     *    "key":"longitude",
     *    "value":16.525613888888902
     *  }
     * ]
     */
    json.getAsJsonArray

  }

}
