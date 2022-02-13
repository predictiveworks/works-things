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

import com.typesafe.config.{Config, ConfigObject}
import de.kp.works.things.conf.ThingsConf

import scala.collection.JavaConversions._

object AirqOptions {
  /**
   * The internal configuration is used, if the current
   * configuration is not set here
   */
  if (!ThingsConf.isInit) ThingsConf.init()
  /**
   * Determine the registry folder from the system
   * property `downloads.dir`
   */
  private val folder = System.getProperty("registry.dir")

  private val climateCfg = ThingsConf.getClimateCfg

  def getBaseUrl:String = climateCfg.getString("serverUrl")

  def getCountry:String = climateCfg.getString("country")

  def getFolder:String = {

    if (folder == null)
      climateCfg.getString("folder")

    else folder

  }

  def getPollutants:List[String] = {
    val values = climateCfg.getStringList("pollutants")
    values.toList
  }

  def getPollutants(stationId:String):List[String] = {

    val sensors = getStations
      .filter(station => station.id == stationId)
      .head
      .sensors

    sensors

  }

  def getStations:List[AirqStation] = {

    val values = climateCfg.getList("stations")
    values.map {
      case configObject: ConfigObject =>

        val station = configObject.toConfig
        AirqStation(
          id      = station.getString("id"),
          name    = station.getString("name"),
          lon     = station.getDouble("lon"),
          lat     = station.getDouble("lat"),
          sensors = station.getStringList("sensors").toList
        )

      case _ =>
        val now = new java.util.Date()
        throw new Exception(s"[ERROR] $now.toString - Stations are not configured properly.")
    }
    .toList

  }

  def getTimeInterval:Long = {

    val interval = climateCfg.getString("interval")
    interval match {
      case "30m" =>
        1000 * 60 * 30
      case "1h" =>
        1000 * 60 * 60
      case _ =>
        val now = new java.util.Date()
        throw new Exception(s"[ERROR] $now.toString - The time interval `$interval` is not supported.")

    }

  }

}
