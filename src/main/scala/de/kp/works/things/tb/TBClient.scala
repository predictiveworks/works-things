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
import de.kp.works.things.logging.Logging
import org.thingsboard.server.common.data.Device
import org.thingsboard.server.common.data.asset.Asset
import org.thingsboard.server.common.data.relation.EntityRelation

import scala.collection.JavaConversions._

trait TBClient extends HttpConnect with Logging {

  protected val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  protected val adminCfg: Config = TBOptions.getAdminCfg
  protected val mobileCfg: Config = TBOptions.getMobileCfg

  protected val baseUrl: String = adminCfg.getString("serverUrl")

  protected val username: String = adminCfg.getString("username")
  protected val userpass: String = adminCfg.getString("userpass")

  protected val loginUrl = "/api/auth/login"
  protected val logoutUrl = "/api/auth/logout"
  /*
   * The current implementation expects that the number
   * of managed assets is less than or equal to 1000
   */
  protected val assetsUrl = "/api/tenant/assets?page=0&pageSize=1000"
  /*
   * The current implementation expects that the number
   * of managed devices is less than or equal to 1000
   */
  protected val devicesUrl = "/api/tenant/devices?page=0&pageSize=1000"
  protected val relationsUrl = "/api/relations"

  protected var authToken:Option[String] = None
  protected var refreshToken:Option[String] = None

  def extractEntityId(response:JsonElement):String = {

    val idObj = response.getAsJsonObject.get("id").getAsJsonObject
    idObj.get("id").getAsString

  }

  def getAssets:Seq[Asset] = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + assetsUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = get(endpoint, header)
    val json = extractJsonBody(bytes)

    val assets = json.getAsJsonObject.get("data").getAsJsonArray

    assets.map(asset =>
      mapper.readValue(asset.toString, classOf[Asset])).toSeq

  }

  def getDevices:Seq[Device] = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + devicesUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = get(endpoint, header)
    val json = extractJsonBody(bytes)

    val devices = json.getAsJsonObject.get("data").getAsJsonArray

    devices.map(device =>
      mapper.readValue(device.toString, classOf[Device])).toSeq

  }

  def getRelations(entityId:String, entityType:String = "ASSET",
                   direction:String = "FROM", relationType:String="Contains"):Seq[EntityRelation] = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + relationsUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")
    /*
     * The minimum request body retrieves all relations
     * of a certain relation type
     */
    val body = Map(
      "filters" -> List(Map("relationType" -> relationType)),
      "parameters" -> Map(
        "rootId"    -> entityId,
        "rootType"  -> entityType,
        "direction" -> direction
      )
    )

    val bytes = post(endpoint, header, mapper.writeValueAsString(body))
    val json = extractJsonBody(bytes)

    val relations = json.getAsJsonArray
    relations.map(relation =>
      mapper.readValue(relation.toString, classOf[EntityRelation])).toSeq

  }

  def getMapper:ObjectMapper = mapper

  def getSecret:String = mobileCfg.getString("secret")

  /**
   * This method is made fault resilient as it must
   * be used outside try-catch clauses
   */
  def login():Boolean = {

    var success = true
    if (authToken.isDefined) return success

    try {

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

    } catch {
      case _:Throwable =>
        success = false
    }

    success

  }

  def logout():Unit = {

    val endpoint = baseUrl + logoutUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")
    /*
     * Build body from credentials
     */
    val body = new JsonObject()
    val bytes = post(endpoint, header, body.toString)
    /*
     * The result should be null
     */
    extractJsonBody(bytes)

    authToken = None
    refreshToken = None

  }
}
