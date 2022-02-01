package de.kp.works.things.prod

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
import de.kp.works.things.{MappingsConf, ThingsConf}

import scala.collection.JavaConversions._

case class ProdMapping(label:String, sensor:String, units:String)

case class ProdMappings(
  /*
   * The identifier of the pre-defined production
   * station; must be the same in the configuration
   * and mappings file
   */
  id:String,
  /*
   * `labels` specifies the display names of the
   * (virtual) frontend devices
   */
  labels:List[String],
  /*
   * The mapping assigns a certain display name (label)
   * to the respective attribute name of the respective
   * TTN device (sensor)
   */
  mappings:List[ProdMapping]
)

object ProdOptions {
  /**
   * The internal configuration is used, if the current
   * configuration is not set here
   */
  if (!ThingsConf.isInit) ThingsConf.init()
  private val prodCfg = ThingsConf.getProductionCfg
  /**
   * The internal mappings is used, if the current
   * mapping is not set here
   */
  if (!MappingsConf.isInit) MappingsConf.init()
  private val mappings = MappingsConf.getMappingsCfg

  def getMappingsByAsset(assetName:String):ProdMappings = {

    val mappings = getAllMappings
      .filter(m => m.id == assetName)

    if (mappings.nonEmpty)
      mappings.head

    else {
      ProdMappings(
        id       = assetName,
        labels   = List.empty[String],
        mappings = List.empty[ProdMapping])
    }

  }
  /**
   * Production specific mappings refer to `rooms`
   * instead of `stations`
   */
  def getAllMappings:List[ProdMappings] = {

    val values = mappings.getList("rooms")
    values.map {
      case configObject: ConfigObject =>

        val station = configObject.toConfig
        val mappings = station
          .getList("mappings")
          .map {
            case mappingObj: ConfigObject =>

              val mapping = mappingObj.toConfig
              ProdMapping(
                label  = mapping.getString("label"),
                sensor = mapping.getString("sensor"),
                units  = mapping.getString("units")
              )
            case _ =>
              val now = new java.util.Date()
              throw new Exception(s"[ERROR] $now.toString - Rooms are not configured properly.")
          }
          .toList


        ProdMappings(
          id       = station.getString("id"),
          labels   = station.getStringList("labels").toList,
          mappings = mappings
        )

      case _ =>
        val now = new java.util.Date()
        throw new Exception(s"[ERROR] $now.toString - Rooms are not configured properly.")
    }
    .toList

  }

  def getRooms:List[ProdRoom] = {

    val values = prodCfg.getList("rooms")
    values.map {
      case configObject: ConfigObject =>

        val room = configObject.toConfig
        ProdRoom(
          id      = room.getString("id"),
          name    = room.getString("name"),
          `type`  = room.getString("type"),
          station = room.getString("station")
        )

      case _ =>
        val now = new java.util.Date()
        throw new Exception(s"[ERROR] $now.toString - Rooms are not configured properly.")
    }
      .toList

  }

  def getStations:List[ProdStation] = {

    val values = prodCfg.getList("stations")
    values.map {
      case configObject: ConfigObject =>

        val station = configObject.toConfig
        ProdStation(
          id      = station.getString("id"),
          name    = station.getString("name"),
          lon     = station.getDouble("lon"),
          lat     = station.getDouble("lat")
        )

      case _ =>
        val now = new java.util.Date()
        throw new Exception(s"[ERROR] $now.toString - Stations are not configured properly.")
    }
    .toList

  }

}
