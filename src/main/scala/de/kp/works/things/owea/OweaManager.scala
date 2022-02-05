package de.kp.works.things.owea

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

import com.google.gson.{JsonObject, JsonParser}
import de.kp.works.things.logging.Logging
import de.kp.works.things.tb.TBAdmin
import org.thingsboard.server.common.data.asset.Asset
import org.thingsboard.server.common.data.id.CustomerId

import scala.collection.JavaConversions.iterableAsScalaIterable

case class OweaStation(id:String, name:String, lon:Double, lat:Double)

object OweaManager {
  /*
   * NOTE: ThingsBoard requires `asset` and `device` names
   * uniquely defined with the tenant and customer scope
   */
  val sensors: String =
    """
      |[
      |{
      |"deviceName":  "DEV.TEMP",
      |"deviceType":  "Temperatursensor",
      |"deviceLabel": "Außentemperaturmesser",
      |"deviceDesc":  "Dieser Sensor misst die Aussentemperatur am Ort der Wetterstation."
      |},
      |{
      |"deviceName":  "DEV.HUMD",
      |"deviceType":  "Feuchtigkeitssensor",
      |"deviceLabel": "Luftfeuchtigkeitsmesser",
      |"deviceDesc":  "Dieser Sensor misst die Luftfeuchtigkeit am Ort der Wetterstation."
      |},
      |{
      |"deviceName":  "DEV.PRESS",
      |"deviceType":  "Drucksensor",
      |"deviceLabel": "Luftdruckmesser",
      |"deviceDesc":  "Dieser Sensor misst den Luftdruck am Ort der Wetterstation."
      |},
      |{
      |"deviceName":  "DEV.CLOU",
      |"deviceType":  "Bewölkungssensor",
      |"deviceLabel": "Bewölkungsmesser",
      |"deviceDesc":  "Dieser Sensor misst den Bewölkungsgrad am Ort der Wetterstation."
      |},
      |{
      |"deviceName":  "DEV.VISB",
      |"deviceType":  "Sichtsensor",
      |"deviceLabel": "Sichtmesser",
      |"deviceDesc":  "Dieser Sensor misst die Sicht am Ort der Wetterstation."
      |},
      |{
      |"deviceName":  "DEV.WIND",
      |"deviceType":  "Windsensor",
      |"deviceLabel": "Windmesser",
      |"deviceDesc":  "Dieser Sensor misst den Wind am Ort der Wetterstation."
      |}
      |]
      |""".stripMargin

}

class OweaManager extends Logging {

  private val datasource = "owea"
  private val ASSET_NAME = "Wetterstation"
  /*
   * STEP #1: Retrieve all pre-defined weather stations
   * from the configuration file
   */
  private val stations = OweaOptions.getStations
  /**
   * This method leverages pre-defined OpenWeather stations
   * (see configuration file) and creates them as ThingsBoard
   * devices with geo spatial coordinates as server attributes
   */
  def createStationsIfNotExist():Boolean = {

    var success:Boolean = true
    try {
      val tbAdmin = new TBAdmin()
      /*
       * STEP #2: Login to ThingsBoard REST API
       * and retrieve access tokens for this session
       */
      if (!tbAdmin.login())
        throw new Exception("Login to ThingsBoard failed.")
      /*
       * STEP #3: Retrieve all assets that refer to
       * the configured tenant administrator
       */
      val tbAssets = tbAdmin.getAssets
      /*
       * STEP #4: Reduce the configured stations to those
       * that have not been created already
       */
      val tbDeviceNames = tbAssets.map(a => a.getName)
      /*
       * __MOD__ The asset names provided by ThingsBoard
       * refer to the pre-configured station identifiers
       */
      val filteredStations = stations.filter(s => !tbDeviceNames.contains(s.id))
      if (filteredStations.isEmpty)
        info(s"All configured weather stations exist already.")

      /*
       * STEP #5: Create remaining Air Quality stations
       * as assets
       */
      filteredStations.foreach(station => {

        try {

          val tbAsset = new Asset()
          val tbAssetName = station.id

          tbAsset.setName(tbAssetName)
          tbAsset.setType(s"$ASSET_NAME")

          tbAsset.setLabel(s"$ASSET_NAME: ${station.name}")

          val tbCustomerId = new CustomerId(
            java.util.UUID.fromString(tbAdmin.getCustomerId))

          tbAsset.setCustomerId(tbCustomerId)
          /*
           * Additional info: gateway, description
           */
          val additionalInfo = new JsonObject
          additionalInfo.addProperty("description", "")

          val node = tbAdmin.getMapper.readTree(additionalInfo.toString)
          tbAsset.setAdditionalInfo(node)

          /* ------------------------------
           *
           *        CREATE ASSET
           *
           */
          val tbResponse = tbAdmin.createAsset(tbAsset)
          val tbAssetId = tbAdmin.extractEntityId(tbResponse)

          Thread.sleep(50)
          /*
           * Build server attributes
           */
          val tbAttributes = new JsonObject

          tbAttributes.addProperty("latitude",  station.lat)
          tbAttributes.addProperty("longitude", station.lon)
          /* ------------------------------
           *
           *    CREATE SERVER ATTRIBUTES
           *
           */
          tbAdmin.createServerAttributes(tbAssetId, "ASSET", tbAttributes.toString)
          Thread.sleep(50)
          /* ------------------------------
           *
           *    CREATE DEVICES
           *
           *
           * A weather station contains the following devices:
           *
           * - temperature with attributes:
           *
           *    temp
           *    feels_like
           *    temp_min
           *    temp_max
           *
           * - humidity
           * - pressure
           * - cloudiness
           * - visibility
           *
           * - wind with attributes:
           *
           *    wind_speed
           *    wind_deg
           *    wind_gust
           *
           * The attributes specified here are client attributes
           * and do not have to be pre-defined (other than server
           * attributes need to)
           */
          val tbDeviceIds = JsonParser.parseString(OweaManager.sensors)
            .getAsJsonArray
            .map(sensor => {
              val sensorObj = sensor.getAsJsonObject
              /*
               * The device name must be unique; to fulfill this
               * ThingsBoard requirement, the device name is defined
               * as a combination of station and device (prefix)
               *
               * Sample: STA.OWEA.AT.FREUDENAU
               */
              val devicePrefix = sensorObj.get("deviceName").getAsString
              /*
               * __MOD__ Remove intermediate `.` between prefix
               * and station identifier
               */
              val deviceName = s"$devicePrefix${station.id.replace("STA", "")}"
              val deviceType = sensorObj.get("deviceType").getAsString

              val deviceLabel = sensorObj.get("deviceLabel").getAsString
              val deviceDesc = sensorObj.get("deviceDesc").getAsString

              val tbDeviceId = tbAdmin.createDevice(tbCustomerId, datasource, deviceName, deviceType, deviceLabel, deviceDesc)

              tbDeviceId
            }).toList
          /* ------------------------------
           *
           *    CREATE RELATIONS
           */
          tbAdmin.createRelations(datasource, tbAssetId, tbAssetName, tbDeviceIds)
          /*
           * Inform about successful creation of this
           * weather station
           */
          info(s"Owea station `${station.name}` successfully created.")

        } catch {
          case t:Throwable =>
            error(s"Creating Owea station `${station.name}` failed: ${t.getLocalizedMessage}")
            success = false
        }

      })
      /*
       * STEP #6: Requests to ThingsBoard are finished,
       * so logout and prepare for next login
       */
      tbAdmin.logout()

    } catch {
      case t:Throwable =>
        error(s"[Creating Owea stations failed: ${t.getLocalizedMessage}")
        success = false
    }
    /*
     * Return flag to indicate whether the creation
     * of weather stations failed
     */
    success

  }

}
