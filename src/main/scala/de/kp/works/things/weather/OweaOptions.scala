package de.kp.works.things.weather

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

import com.typesafe.config.ConfigObject
import de.kp.works.things.ThingsConf

import scala.collection.JavaConversions._

object OweaOptions {

  /**
   * The internal configuration is used, if the current
   * configuration is not set here
   */
  if (!ThingsConf.isInit) ThingsConf.init()

  private val weatherCfg = ThingsConf.getWeatherCfg

  def getApiKey:String = weatherCfg.getString("apiKey")

  def getBaseUrl:String = weatherCfg.getString("serverUrl")

  def getStations:List[OweaStation] = {

    val values = weatherCfg.getList("stations")
    values.map {
      case configObject: ConfigObject =>

        val station = configObject.toConfig
        OweaStation(
          id = station.getString("id"),
          name = station.getString("name"),
          lon = station.getDouble("lon"),
          lat = station.getDouble("lat")
        )

      case _ =>
        val now = new java.util.Date()
        throw new Exception(s"[ERROR] $now.toString - Stations are not configured properly.")
    }
      .toList

  }

  def getTimeInterval:Long = {

    val interval = weatherCfg.getString("interval")
    interval match {
      case "15m" =>
        1000 * 60 * 15
      case "30m" =>
        1000 * 60 * 30
      case "1h" =>
        1000 * 60 * 60
      case "3h" =>
        1000 * 60 * 60 * 3
      case "6h" =>
        1000 * 60 * 60 * 6
      case "12h" =>
        1000 * 60 * 60 * 12
      case _ =>
        val now = new java.util.Date()
        throw new Exception(s"[ERROR] $now.toString - The time interval `$interval` is not supported.")

    }

  }
}
