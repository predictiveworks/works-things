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

import com.google.gson.JsonObject
import de.kp.works.things.devices.{DeviceEntry, DeviceRepository}
import de.kp.works.things.tb.{TBAdmin, TBOptions}
import org.thingsboard.server.common.data.Device
import org.thingsboard.server.common.data.id.CustomerId

import scala.collection.mutable

class TTNManager {

  private val repository = DeviceRepository.getInstance

  /*
   * STEP #1: Retrieve all devices that are
   * registered with The ThingsNetwork
   */
  private val ttnDevices:Seq[TTNDevice] =
    try {
      val devices = new TTNAdmin().getDevices
      devices

    } catch {
      case t:Throwable =>

        val now = new java.util.Date
        println(
          s"[ERROR] - $now.toString - Retrieving TTN devices failed: ${t.getLocalizedMessage}")

        Seq.empty[TTNDevice]

    }

  /**
   * This method leverages the retrieved TTN devices
   * and creates them as ThingsBoard devices with geo
   * spatial coordinates as server attributes.
   *
   * In addition, the TTN device identifier and the
   * associated MQTT topic to listen to the TTN broker
   * are created as server attributes.
   */
  def createDevicesIfNotExist():Unit = {
    /*
     * STEP #2: Retrieve all devices that are
     * registered with the ThingsBoard server
     */
    val tbAdmin = new TBAdmin()
    tbAdmin.login()

    val tbDevices = tbAdmin.getDevices
      /*
       * STEP #3: Enrich all devices with server
       * attributes
       */
      .map(device => {

        val tbDeviceId = device.getId.getId.toString

        val attrVal = tbAdmin.getServerAttribute(tbDeviceId, TBAdmin.TTN_DEVICE_ID)
        val ttnDeviceId = if (attrVal.isJsonNull) null else attrVal.getAsString

        (device, ttnDeviceId)

      })

    val ttnDeviceIds = tbDevices
      .map{case(_, ttnDeviceId) => ttnDeviceId}
      .filter(id => id != null)

    /*
     * STEP #4: Determine those TTN devices that
     * are not registered with ThingsBoard already.
     *
     * Note, this is achieved by checking whether
     * a TTN device identifier is assigned as server
     * attribute
     */
    val ttnFilteredDevices = ttnDevices
      .filter(device => !ttnDeviceIds.contains(device.device_id))

    /*
     * STEP #5: Create remaining TTN devices; note,
     * ThingsBoard currently does not support to create
     * server attributes in a single request
     */
    val ttnNames = mutable.ArrayBuffer.empty[String]
    ttnFilteredDevices.foreach(ttnDevice => {

      try {

        val tbDevice = new Device()

        /* NAME & TYPE */
        val ttnName = ttnDevice.name

        /* Register TTN device name for subsequent use */
        ttnNames += ttnName

        tbDevice.setName(ttnName)
        tbDevice.setType("TTN Device")

        /* CUSTOMER ID */
        val tbCustomerId = new CustomerId(java.util.UUID.fromString(tbAdmin.getCustomerId))
        tbDevice.setCustomerId(tbCustomerId)

        /*
         * Additional info: gateway, description
         */
        val additionalInfo = new JsonObject
        additionalInfo.addProperty("gateway", false)
        additionalInfo.addProperty("description", ttnDevice.description)

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
         * Build server attributes
         */
        val tbAttributes = new JsonObject
        tbAttributes.addProperty("altitude", ttnDevice.altitude)

        tbAttributes.addProperty("latitude",  ttnDevice.latitude)
        tbAttributes.addProperty("longitude", ttnDevice.longitude)
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
          datasource = "ttn",
          tbDeviceId = tbDeviceId,
          tbDeviceToken = tbDeviceToken,
          tbMqttTopic = TBOptions.DEVICE_TELEMETRY_TOPIC,
          ttnDeviceId = ttnDevice.device_id,
          ttnMqttTopic = ttnDevice.mqtt_topic
        )

        repository.register(deviceEntry)

        val now = new java.util.Date
        println(
          s"[INFO] - $now.toString - TTN device `$ttnName` successfully created.")

      } catch {
        case t:Throwable =>
          val now = new java.util.Date
          println(
            s"[ERROR] - $now.toString - Creating TTN device `${ttnDevice.name}` failed: ${t.getLocalizedMessage}")
      }

    })

  }
}
