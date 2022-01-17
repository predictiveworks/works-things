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

import de.kp.works.things.tb.TBAdmin

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
      tbDeviceToken,
      tbMqttTopic,
      ttnDeviceId,
      ttnMqttTopic
    )

    values.mkString(",")

  }
}

object DeviceRepository {

  private var instance:Option[DeviceRepository] = None

  def getInstance:DeviceRepository = {

    if (instance.isEmpty) instance = Some(new DeviceRepository())
    instance.get

  }
}


class DeviceRepository extends TBAdmin {

  private val folder = RepositoryOptions.getFolder
  private val repository = mutable.HashMap.empty[String, DeviceEntry]

  load()

  /**
   * This methods loads all available device mapping
   * into the internal repository
   */
  def load():Unit = {

    repository.clear()

    val filePath = folder + "devices.csv"
    val source = scala.io.Source
      .fromFile(new java.io.File(filePath))

    val devices = source.getLines
    devices.foreach(device => {

      val Array(
        datasource,
        tbDeviceId,
        tbDeviceToken,
        tbMqttTopic,
        ttnDeviceId,
        ttnMqttTopic
      ) = device.split(",")

      val deviceEntry = DeviceEntry(
        datasource,
        tbDeviceId,
        tbDeviceToken,
        tbMqttTopic,
        ttnDeviceId,
        ttnMqttTopic
      )

      repository += deviceEntry.tbDeviceId -> deviceEntry

    })

    source.close

  }

  def register(entry:DeviceEntry):Unit = {

    repository += entry.tbDeviceId -> entry
    /*
     * Append repository entry to repository file;
     * note, the registry process runs sequentially.
     *
     * Therefore, there is no need to do concurrency
     * control.
     */
    val filePath = folder + "devices.csv"
    val writer = new FileWriter(filePath, true)

    writer.write(entry.toString)
    writer.close()

  }
}
