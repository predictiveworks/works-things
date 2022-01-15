package de.kp.works.things.tb.admin

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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.JsonObject
import de.kp.works.things.http.HttpConnect
import de.kp.works.things.tb.TBOptions
import org.thingsboard.server.common.data.Device

import scala.collection.JavaConversions.iterableAsScalaIterable

class ThingsAdmin extends HttpConnect {

  private val adminCfg = TBOptions.getAdminCfg
  private val baseUrl = adminCfg.getString("serverUrl")

  private val deviceUrl = "/api/device"
  private val devicesUrl = "/api/tenant/devices?page=0&pageSize=1000"

  private val attributesUrl = "/api/plugins/telemetry/DEVICE/"

  private val loginUrl = "/api/auth/login"

  private val username = adminCfg.getString("username")
  private val userpass = adminCfg.getString("userpass")

  private val customerId = adminCfg.getString("customerId")

  protected var authToken:Option[String] = None
  protected var refreshToken:Option[String] = None

  protected val mapper = new ObjectMapper()

  def getCustomerId: String = customerId

  def getMapper:ObjectMapper = mapper

  def createDevice(device:Device):Unit = {

    if (authToken.isEmpty) {
      val now = new java.util.Date()
      throw new Exception(
        s"[ERROR] $now.toString - No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + deviceUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    post(endpoint, header, mapper.writeValueAsString(device))

  }

  def createServerAttributes(deviceId:String, payload:String):Unit = {

    val endpoint = baseUrl + attributesUrl + s"$deviceId/SERVER_SCOPE"
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    post(endpoint, header, payload)

  }

  def getDevices:Seq[Device] = {

    if (authToken.isEmpty) {
      val now = new java.util.Date()
      throw new Exception(
        s"[ERROR] $now.toString - No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + devicesUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = get(endpoint, header)
    val json = extractJsonBody(bytes)

    val devices = json.getAsJsonObject.get("data").getAsJsonArray

    devices.map(device =>
      mapper.readValue(device.toString, classOf[Device])).toSeq

  }
  /**
   * Send a POST request with
   *
   * {
   *  "username":"tenant@thingsboard.org",
   *  "password":"tenant"
   * }
   *
   * body to retrieve the JWT token for subsequent requests
   */
  def login():Unit = {

    val endpoint = baseUrl + loginUrl
    val header = Map.empty[String,String]
    /*
     * Build body from credentials
     */
    val body = new JsonObject()
    body.addProperty("username", username)
    body.addProperty("password", userpass)

    val bytes = post(endpoint, header, body.toString)
    /*
     * RESPONSE
     *
     * { "token":"$YOUR_JWT_TOKEN", "refreshToken":"$YOUR_JWT_REFRESH_TOKEN" }
     */
    val json = extractJsonBody(bytes).getAsJsonObject

    authToken = Some(json.get("token").getAsString)
    refreshToken = Some(json.get("refreshToken").getAsString)

  }

}
