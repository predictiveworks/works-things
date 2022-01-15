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
import de.kp.works.things.tb.admin.ThingsAdmin
import org.thingsboard.server.common.data.Device
import org.thingsboard.server.common.data.id.CustomerId

object AirQManager {

  def main(args:Array[String]):Unit = {

    val manager = new AirQManager
    manager.createStations()

  }

}

class AirQManager extends ThingsAdmin {

  private val stations = AirqOptions.getStations

  def createStations():Unit = {
    /*
     * STEP #1: Login to ThingsBoard REST API
     * and retrieve access tokens for this session
     */
    login()
    /*
     * STEP #2: Retrieve all devices that refer to
     * the configured tenant administrator
     */
    val devices = getDevices
    /*
     * STEP #3: Reduce the configured stations to those
     * that have not been created already
     */
    val deviceNames = devices.map(d => d.getName)
    val filteredStations = stations.filter(s => !deviceNames.contains(s.name))
    /*
     * STEP #4: Create remaining Air Quality stations
     * as gateways
     */
    filteredStations.foreach(station => {

      val device = new Device()

      device.setName(station.id)
      device.setType("Air Quality Station")

      device.setLabel(s"AirQ Station: ${station.name}")

      val customerId = new CustomerId(java.util.UUID.fromString(getCustomerId))
      device.setCustomerId(customerId)
      /*
       * Additional info: gateway, description
       */
      val additionalInfo = new JsonObject
      additionalInfo.addProperty("gateway", true)
      additionalInfo.addProperty("description", "")

      val node = getMapper.readTree(additionalInfo.toString)
      device.setAdditionalInfo(node)

      createDevice(device)

      val now = new java.util.Date
      println(s"[INFO] - $now.toString - AirQ Station `${station.name}` successfully created.")

      Thread.sleep(100)

    })
    /*
     * STEP #5: Retrieve all the previously created stations
     * again and set the geo spatial coordinates as server
     * attributes
     */
    val stationIds = stations.map(s => s.id)
    val createdStations = getDevices
      .filter(d => stationIds.contains(d.getName))

    createdStations.foreach(device => {

      val id = device.getId.getId.toString
      val name = device.getName

      val cfg = stations.filter(s => s.id == name).head

      val lon = cfg.lon
      val lat = cfg.lat

      val attributes = new JsonObject
      attributes.addProperty("latitude",  lat)
      attributes.addProperty("longitude", lon)

      createServerAttributes(id, attributes.toString)

      val now = new java.util.Date
      println(s"[INFO] - $now.toString - Attributes for AirQ Station `$name` successfully created.")

      Thread.sleep(100)

    })
   }
}
