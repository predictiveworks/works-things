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

import com.google.gson.{JsonArray, JsonElement, JsonNull, JsonObject}
import de.kp.works.things.devices.{DeviceEntry, DeviceRegistry, RelationEntry, RelationRegistry}
import de.kp.works.things.logging.Logging
import de.kp.works.things.ttn.TTNDevice
import org.thingsboard.server.common.data.Device
import org.thingsboard.server.common.data.asset.Asset
import org.thingsboard.server.common.data.id.{AssetId, CustomerId, DeviceId}
import org.thingsboard.server.common.data.relation.EntityRelation

import scala.collection.JavaConversions._
import scala.collection.mutable

case class TBPoint(ts:Long, value:Double)

object TBAdmin {

  val TTN_DEVICE_ID  = "ttn_device_id"
  val TTN_MQTT_TOPIC = "ttn_mqtt_topic"

}

class TBAdmin extends TBClient with Logging {

  private val deviceRegistry = DeviceRegistry.getInstance
  private val relationRegistry = RelationRegistry.getInstance

  private val assetUrl    = "/api/asset"
  private val getAssetUrl   = "/api/tenant/assets?assetName="

  private val deviceUrl   = "/api/device"
  private val relationUrl = "/api/relation"

  private val telemetryUrl = "/api/plugins/telemetry/"

  private val customerId = adminCfg.getString("customerId")

  def getCustomerId: String = customerId

  def createAsset(asset:Asset):JsonElement = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + assetUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = post(endpoint, header, mapper.writeValueAsString(asset))

    val json = extractJsonBody(bytes)
    json

  }

  def getAssetByName(assetName:String):Asset = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + getAssetUrl + assetName
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = get(endpoint, header)
    val json = extractJsonBody(bytes)

    val asset = json.getAsJsonObject
    mapper.readValue(asset.toString, classOf[Asset])

  }
  /**
   * This method supports the creation of a The ThingsNetwork
   * device; the current implementation does not assign any
   * server attributes. Geospatial coordinates are reserved
   * for assets (or stations).
   *
   * This may change in future versions when more and more
   * devices become distributed (within a certain station).
   *
   * Also important with respect to attribute mapping:
   *
   * ThingsBoard maps telemetry attributes to client attributes
   * and these are generated on demand.
   */
  def createTTNDevice(
   tbCustomerId:CustomerId, datasource:String, tbDeviceName:String,
   ttnDevice:TTNDevice):String = {

    val tbDeviceType = s"TTN Sensor"

    val tbDeviceLabel = s"${ttnDevice.name}"
    val tbDeviceDesc  = ttnDevice.description
    /*
     * The ThingsNetwork device identifier that is used
     * to access device telemetry data via MQTT
     */
    val ttnDeviceId = ttnDevice.device_id
    /*
     * The ThingsNetwork MQTT topic to retrieve telemetry
     * data from registered devices
     */
    val ttnMqttTopic = ttnDevice.mqtt_topic

    /*
     * Build ThingsBoard device
     */
    val tbDevice = new Device()

    tbDevice.setName(tbDeviceName)
    tbDevice.setType(tbDeviceType)

    tbDevice.setLabel(tbDeviceLabel)
    tbDevice.setCustomerId(tbCustomerId)
    /*
    * Additional info: description
    */
    val additionalInfo = new JsonObject
    additionalInfo.addProperty("description", tbDeviceDesc)

    val node = getMapper.readTree(additionalInfo.toString)
    tbDevice.setAdditionalInfo(node)
    /* ------------------------------
    *
    *        CREATE DEVICE
    *
    */
    val tbResponse = createDevice(tbDevice)
    val tbDeviceId = extractEntityId(tbResponse)

    Thread.sleep(50)
    /* ------------------------------
    *
    *        GET DEVICE TOKEN
    *
    */
    val tbDeviceToken = getDeviceToken(tbDeviceId)
    Thread.sleep(50)
    /*
     * Build repository entry and register in
     * the [DeviceRepository] of the Things
     * Server
     */
    val deviceEntry = DeviceEntry(
      datasource    = datasource,
      tbDeviceId    = tbDeviceId,
      tbDeviceName  = tbDeviceName,
      tbDeviceToken = tbDeviceToken,
      tbMqttTopic   = TBOptions.DEVICE_TELEMETRY_TOPIC,
      ttnDeviceId   = ttnDeviceId,
      ttnMqttTopic  = ttnMqttTopic
    )

    deviceRegistry.register(deviceEntry)
    tbDeviceId

  }
  /**
   * A helper method to retrieve all registered ThingsBoard
   * devices that refer to a certain datasource.
   *
   * Supported values are `airq`, `owea` and `prod`
   */
  def getDevicesBySource(datasource:String):Seq[DeviceEntry] = {
    deviceRegistry.getBySource(datasource)
  }
  /**
   * A helper method to create a certain device for a certain
   * customer from name, type, label and description.
   *
   * This method registers the device in the internal device
   * registry and returns the ThingsBoard device identifier
   * for building relations with associated assets
   */
  def createDevice(
    tbCustomerId:CustomerId, datasource:String, tbDeviceName:String,
    tbDeviceType:String, tbDeviceLabel:String, tbDeviceDesc:String):String = {

    val tbDevice = new Device()

    tbDevice.setName(tbDeviceName)
    tbDevice.setType(tbDeviceType)

    tbDevice.setLabel(tbDeviceLabel)
    tbDevice.setCustomerId(tbCustomerId)
    /*
    * Additional info: description
    */
    val additionalInfo = new JsonObject
    additionalInfo.addProperty("description", tbDeviceDesc)

    val node = getMapper.readTree(additionalInfo.toString)
    tbDevice.setAdditionalInfo(node)
    /* ------------------------------
    *
    *        CREATE DEVICE
    *
    */
    val tbResponse = createDevice(tbDevice)
    val tbDeviceId = extractEntityId(tbResponse)

    Thread.sleep(50)
    /* ------------------------------
    *
    *        GET DEVICE TOKEN
    *
    */
    val tbDeviceToken = getDeviceToken(tbDeviceId)
    Thread.sleep(50)
    /*
     * Build repository entry and register in
     * the [DeviceRepository] of the Things
     * Server
     */
    val deviceEntry = DeviceEntry(
      datasource    = datasource,
      tbDeviceId    = tbDeviceId,
      tbDeviceName  = tbDeviceName,
      tbDeviceToken = tbDeviceToken,
      tbMqttTopic   = TBOptions.DEVICE_TELEMETRY_TOPIC
    )

    deviceRegistry.register(deviceEntry)
    tbDeviceId

  }

  def createDevice(device:Device):JsonElement = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + deviceUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = post(endpoint, header, mapper.writeValueAsString(device))

    val json = extractJsonBody(bytes)
    json

  }

  def createRelations(datasource:String, tbAssetId:String, tbAssetName:String, tbDeviceIds:List[String]):Unit = {
    /*
     * STEP #1: Create relations in the ThingsBoard
     * database leveraging the REST API
     */
    tbDeviceIds.foreach(tbDeviceId => {

      val relation = new EntityRelation()

      val tbFromId = AssetId.fromString(tbAssetId)
      relation.setFrom(tbFromId)

      val tbToId = DeviceId.fromString(tbDeviceId)
      relation.setTo(tbToId)
      /*
       * The relation type is set fixed to `Contains`
       */
      relation.setType("Contains")

      createRelation(relation)
      Thread.sleep(50)

    })
    /*
     * STEP #2: Register relations in the relation
     * registry to ease and accelerate Things API
     * requests
     */
    val relationEntry = RelationEntry(
      datasource = datasource,
      tbFromId   = tbAssetId,
      tbFromName = tbAssetName,
      tbToIds    = tbDeviceIds)

    relationRegistry.register(relationEntry)

  }

  def createRelation(relation:EntityRelation):JsonElement = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + relationUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = post(endpoint, header, mapper.writeValueAsString(relation))

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
      throw new Exception(s"No access token found to access ThingsBoard.")
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

  def createServerAttributes(entityId:String, entityType:String, payload:String):JsonElement = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + telemetryUrl + s"$entityType/$entityId/SERVER_SCOPE"
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = post(endpoint, header, payload)
    val json = extractJsonBody(bytes)
    json

  }

  def getServerAttribute(entityId:String, entityType:String, name:String):JsonElement = {

    val attributes = getServerAttributes(entityId, entityType)
      .filter(attr => {

        val attrObj = attr.getAsJsonObject
        val key = attrObj.get("key").getAsString

        if (key == name) true else false

      }).toSeq

    if (attributes.isEmpty) return JsonNull.INSTANCE

    val attrObj = attributes.head.getAsJsonObject
    attrObj.get("value")

  }

  def getServerAttributes(entityId:String, entityType:String):JsonArray = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + telemetryUrl + s"$entityType/$entityId/values/attributes/SERVER_SCOPE"
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

  /**
   * This is the initial request to retrieve the time series
   * attributes of a certain device (identified by identifier)
   */
  def getTsKeys(deviceId:String):Seq[String] = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    try {

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

      result

    } catch {
      case t:Throwable =>
        error(t.getLocalizedMessage)
        Seq.empty[String]
    }

  }
  /**
   * This request retrieves the latest time series values
   * of a certain device that refer to the provided keys
   */
  def getTsLatest(deviceId:String, keys:Seq[String]):Map[String, List[TBPoint]] = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val output = mutable.HashMap.empty[String, List[TBPoint]]
    try {

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

      output.toMap

    } catch {
      case t:Throwable =>
        error(t.getLocalizedMessage)
        output.toMap
    }

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
  def getTsHistorical(deviceId:String, keys:Seq[String], params:Map[String, String], limit:Int = 1000):Map[String, List[TBPoint]] = {

    if (authToken.isEmpty) {
       throw new Exception(s"No access token found to access ThingsBoard.")
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

    val stopTs = System.currentTimeMillis
    val beginTs = stopTs - 1000 * 60 * 60 * 24 * 30 // A time period of month

    val startTs = params.getOrElse("startTs", s"$beginTs").toLong
    deviceUrl = deviceUrl + s"&startTs=$startTs"

    val endTs = params.getOrElse("endTs", s"$stopTs").toLong
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

    output.toMap

  }

}
