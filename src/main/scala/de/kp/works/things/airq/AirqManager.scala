package de.kp.works.things.airq

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

import com.google.gson.JsonObject
import de.kp.works.things.logging.Logging
import de.kp.works.things.tb.TBAdmin
import org.thingsboard.server.common.data.asset.Asset
import org.thingsboard.server.common.data.id.CustomerId

case class AirqStation(id:String, name:String, lon:Double, lat:Double, sensors:List[String])
/**
 * The [AirqManager] is responsible for creating a set
 * of pre-defined Air Quality stations; this is done as
 * part of the Things Server's pre-start functionality
 */
class AirqManager extends Logging {

  private val datasource = "airq"
  private val ASSET_NAME = "Luftqualitätsstation"
  /*
   * STEP #1: Retrieve all pre-defined Air Quality stations
   * from the configuration file
   */
  private val stations = AirqOptions.getStations
  /**
   * This method leverages pre-defined Air Quality stations
   * (see configuration file) and creates them as ThingsBoard
   * assets with geo spatial coordinates as server attributes
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
      val tbAssetNames = tbAssets.map(a => a.getName)
      /*
       * __MOD__ The asset names provided by ThingsBoard
       * refer to the pre-configured room identifiers
       */
      val filteredStations = stations.filter(s => !tbAssetNames.contains(s.id))
      if (filteredStations.isEmpty)
        info(s"All configured air quality stations exist already.")
      /*
       * STEP #5: Create remaining Air Quality stations
       * as ThingsBoard assets
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
           * Additional info: description
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
           * Create asset server attributes
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
           * For air climate stations, the sensors are
           * pre-defined, and do not have to retrieved
           * dynamically as is for TTN devices
           */
          val tbDeviceIds = station.sensors.map(sensor => {

            val devicePrefix =
              if (sensor == "pm25") "DEV.PM2.5" else s"DEV.${sensor.toUpperCase}"
            /*
             * __MOD__ Remove intermediate `.` between prefix
             * and station identifier
             */
            val deviceName = s"$devicePrefix${station.id.replace("STA", "")}"

            val deviceType =
              if (sensor == "pm25") "PM2.5 Sensor" else s"${sensor.toUpperCase} Sensor"

            val deviceLabel =
              if (sensor == "pm25") "PM2.5-Messer" else s"${sensor.toUpperCase}-Messer"

            val deviceDesc = {

              val pollutant =
                if (sensor == "pm25") "PM2.5" else s"${sensor.toUpperCase}"

              s"Dieser Sensor misst den Wert von $pollutant in der Luft am Ort der Luftqualitätsstation."
            }

            tbAdmin.createDevice(tbCustomerId, datasource, deviceName, deviceType, deviceLabel, deviceDesc)

          })
          /* ------------------------------
           *
           *    CREATE RELATIONS
           */
          tbAdmin.createRelations(
            datasource, tbAssetId, tbAssetName, "ASSET", tbDeviceIds, "DEVICE")
          /*
           * Inform about successful creation of this
           * air quality station
           */
          info(s"Airq station `${station.name}` successfully created.")

        } catch {
          case t:Throwable =>
            error(s"[Creating Airq station `${station.name}` failed: ${t.getLocalizedMessage}")
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
        error(s"[Creating Airq stations failed: ${t.getLocalizedMessage}")
        success = false
    }
    /*
     * Return flag to indicate whether the creation
     * of air quality stations failed
     */
    success

  }

}
