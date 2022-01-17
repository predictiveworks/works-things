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
import de.kp.works.things.devices.{DeviceEntry, DeviceRepository}
import de.kp.works.things.tb.{TBAdmin, TBOptions}
import org.thingsboard.server.common.data.Device
import org.thingsboard.server.common.data.id.CustomerId

object AirQManager {

  def main(args:Array[String]):Unit = {

    val manager = new AirQManager
    manager.createStationsIfNotExist()

  }

}
/**
 * The [AirqManager] is responsible for creating a set
 * of pre-defined Air Quality stations; this is done as
 * part of the Things Server's pre-start functionality
 */
class AirQManager {

  private val repository = DeviceRepository.getInstance

  /*
   * STEP #1: Retrieve all pre-defined Air Quality stations
   * from the configuration file
   */
  private val stations = AirqOptions.getStations
  /**
   * This method leverages pre-defined Air Quality stations
   * (see configuration file) and creates them as ThingsBoard
   * gateway devices with geo spatial coordinates as server
   * attributes
   */
  def createStationsIfNotExist():Unit = {

    val tbAdmin = new TBAdmin()
    /*
     * STEP #2: Login to ThingsBoard REST API
     * and retrieve access tokens for this session
     */
    tbAdmin.login()
    /*
     * STEP #3: Retrieve all devices that refer to
     * the configured tenant administrator
     */
    val tbDevices = tbAdmin.getDevices
    /*
     * STEP #4: Reduce the configured stations to those
     * that have not been created already
     */
    val tbDeviceNames = tbDevices.map(d => d.getName)
    val filteredStations = stations.filter(s => !tbDeviceNames.contains(s.name))
    /*
     * STEP #5: Create remaining Air Quality stations
     * as gateways
     */
    filteredStations.foreach(station => {

      try {

        val tbDevice = new Device()

        tbDevice.setName(station.id)
        tbDevice.setType("Air Quality Station")

        tbDevice.setLabel(s"AirQ Station: ${station.name}")

        val tbCustomerId = new CustomerId(
          java.util.UUID.fromString(tbAdmin.getCustomerId))

        tbDevice.setCustomerId(tbCustomerId)
        /*
         * Additional info: gateway, description
         */
        val additionalInfo = new JsonObject
        additionalInfo.addProperty("gateway", true)
        additionalInfo.addProperty("description", "")

        val node = tbAdmin.getMapper.readTree(additionalInfo.toString)
        tbDevice.setAdditionalInfo(node)

        /* ------------------------------
         *
         *        CREATE DEVICE
         *
         */
        val tbResponse = tbAdmin.createDevice(tbDevice)
        val tbDeviceId = tbAdmin.extractDeviceId(tbResponse)

        Thread.sleep(50)
        /*
         * Create device server attributes
         */
        val tbAttributes = new JsonObject
        tbAttributes.addProperty("latitude",  station.lat)
        tbAttributes.addProperty("longitude", station.lon)
        /* ------------------------------
         *
         *    CREATE SERVER ATTRIBUTES
         *
         */
        tbAdmin.createServerAttributes(tbDeviceId, tbAttributes.toString)
        Thread.sleep(50)
        /* ------------------------------
         *
         *        GET DEVICE TOKEN
         *
         */
        val tbDeviceToken = tbAdmin.getDeviceToken(tbDeviceId)
        Thread.sleep(50)
        /*
         * Build repository entry and register in
         * the [DeviceRepository] of the Things
         * Server
         */
        val deviceEntry = DeviceEntry(
          datasource = "airq",
          tbDeviceId = tbDeviceId,
          tbDeviceToken = tbDeviceToken,
          tbMqttTopic = TBOptions.GATEWAY_TELEMETRY_TOPIC
        )

        repository.register(deviceEntry)

        val now = new java.util.Date
        println(s"[INFO] - $now.toString - AirQ station `${station.name}` successfully created.")


      } catch {
        case t:Throwable =>
          val now = new java.util.Date
          println(
            s"[ERROR] - $now.toString - Creating Airq station `${station.name}` failed: ${t.getLocalizedMessage}")
      }

    })
   }
}
