package de.kp.works.things.devices

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

import java.io.FileWriter
import scala.collection.mutable

/*
 * A device mapping connects an external device configuration
 * that holds e.g. The Things Network device specifications to
 * ThingsBoard representations of these devices
 */
case class DeviceEntry(
  /*
   * The data source (origin) of this repository
   * item. Supported values are `airq`, `owea` and
   * `ttn`
   */
  datasource:String,
  /*
   * The ThingsBoard device identifier that is used
   * to access device specific information
   */
  tbDeviceId:String,
  /*
   * The ThingsBoard device name is a unique name per
   * tenant that is used to organize and retrieve device
   * specific access parameters
   */
  tbDeviceName:String,
  /*
   * The ThingsBoard device token is user as user
   * name to publish telemetry data to ThingsBoard
   */
  tbDeviceToken:String,
  /*
   * The ThingsBoard MQTT topic to publish telemetry
   * data to ThingsBoard devices
   */
  tbMqttTopic:String,
  /*
   * The ThingsNetwork device identifier that is used
   * to access device telemetry data via MQTT
   */
  ttnDeviceId:String = "",
  /*
   * The ThingsNetwork MQTT topic to retrieve telemetry
   * data from registered devices
   */
  ttnMqttTopic:String = ""
) {

  override def toString:String = {

    val values = Seq(
      datasource,
      tbDeviceId,
      tbDeviceName,
      tbDeviceToken,
      tbMqttTopic,
      ttnDeviceId,
      ttnMqttTopic
    )

    values.mkString(",")

  }
}

object DeviceRegistry {

  private val folder = RepositoryOptions.getFolder
  private val registry = mutable.HashMap.empty[String, DeviceEntry]
  /**
   * This methods loads all available device mapping
   * into the internal repository
   */
  def load():Unit = {

    registry.clear()

    val filePath = folder + "devices.csv"
    val source = scala.io.Source
      .fromFile(new java.io.File(filePath))

    val devices = source.getLines.toList

    devices.foreach(device => {

      val length = device.split(",").length
      val deviceEntry = if (length == 5) {

        val Array(
          datasource,
          tbDeviceId,
          tbDeviceName,
          tbDeviceToken,
          tbMqttTopic) = device.split(",")

        DeviceEntry(
          datasource,
          tbDeviceId,
          tbDeviceName,
          tbDeviceToken,
          tbMqttTopic,
          "",
          ""
        )

      } else if (length == 7) {

        val Array(
        datasource,
        tbDeviceId,
        tbDeviceName,
        tbDeviceToken,
        tbMqttTopic,
        ttnDeviceId,
        ttnMqttTopic) = device.split(",")

        DeviceEntry(
          datasource,
          tbDeviceId,
          tbDeviceName,
          tbDeviceToken,
          tbMqttTopic,
          ttnDeviceId,
          ttnMqttTopic
        )

      } else {
        throw new Exception(s"Corrupted device registry detected.")
      }

      registry += deviceEntry.tbDeviceName -> deviceEntry

    })

    source.close

  }
  /**
   * This method retrieves all device entries that
   * refer a certain datasource; supported values
   * are `airq`, `owea` and `prod`.
   */
  def getBySource(datasource:String):Seq[DeviceEntry] = {

    val deviceEntries = registry
      .map{case(_, deviceEntry) => deviceEntry}
      .filter(deviceEntry => deviceEntry.datasource == datasource)

    deviceEntries.toSeq

  }
  /**
   * Retrieve a certain registered device by its
   * ThingsBoard device name
   */
  def get(deviceName:String):Option[DeviceEntry] =
    registry.get(deviceName)
  /**
   * The device specific access parameters are
   * organized by the device name as this parameter
   * is externally known and can derived with ease
   */
  def register(entry:DeviceEntry):Unit = {

    registry += entry.tbDeviceName -> entry
    /*
     * Append repository entry to repository file;
     * note, the registry process runs sequentially.
     *
     * Therefore, there is no need to do concurrency
     * control.
     */
    val filePath = folder + "devices.csv"
    val writer = new FileWriter(filePath, true)

    val line = entry.toString + "\n"

    writer.write(line)
    writer.close()

  }
}
