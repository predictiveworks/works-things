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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.gson.{JsonElement, JsonObject}
import com.typesafe.config.Config
import de.kp.works.things.http.HttpConnect
import org.thingsboard.server.common.data.Device

import scala.collection.JavaConversions._

trait TBClient extends HttpConnect {

  protected val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  protected val adminCfg: Config = TBOptions.getAdminCfg
  protected val mobileCfg: Config = TBOptions.getMobileCfg

  protected val baseUrl: String = adminCfg.getString("serverUrl")

  protected val username: String = adminCfg.getString("username")
  protected val userpass: String = adminCfg.getString("userpass")

  protected val loginUrl = "/api/auth/login"
  /*
   * The current implementation expects that the number
   * of managed devices is less than or equal to 1000
   */
  protected val devicesUrl = "/api/tenant/devices?page=0&pageSize=1000"

  protected var authToken:Option[String] = None
  protected var refreshToken:Option[String] = None

  def extractDeviceId(response:JsonElement):String = {

    val idObj = response.getAsJsonObject.get("id").getAsJsonObject
    idObj.get("id").getAsString

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

  def getMapper:ObjectMapper = mapper

  def getSecret:String = mobileCfg.getString("secret")

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
