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

import com.google.gson.JsonObject
import de.kp.works.things.logging.Logging
import de.kp.works.things.tb.TBAdmin
import de.kp.works.things.ttn.{TTNAdmin, TTNDevice}
import org.thingsboard.server.common.data.asset.Asset
import org.thingsboard.server.common.data.id.CustomerId

import scala.collection.mutable
/**
 * This case class defines a production room with
 * identifier, type and name, and also references
 * the superior station (assets)
 */
case class ProdRoom(id:String, name:String, `type`:String, station:String)

case class ProdStation(id:String, name:String, lon:Double, lat:Double)

class ProdManager extends ProdBase with Logging {

  private val datasource = "prod"

  private val STATION_NAME = "Produktionsstätte"
  private val ROOM_NAME    = "Produktionsraum"
  /*
   * Retrieve all pre-defined production stations
   * and rooms from the configuration file
   */
  private val stations = ProdOptions.getStations
  private val rooms = ProdOptions.getRooms
  /**
   * This method leverages pre-defined productions stations
   * (see configuration file) and creates them as ThingsBoard
   * assets with geo spatial coordinates as server attributes
   */
  def createStationsIfNotExist():Boolean = {

    var success:Boolean = true
    try {
      /*
       * STEP #1: Login to ThingsBoard REST API
       * and retrieve access tokens for this session
       */
      val tbAdmin = new TBAdmin()
      if (!tbAdmin.login())
        throw new Exception("Login to ThingsBoard failed.")

      val tbCustomerId = new CustomerId(
        java.util.UUID.fromString(tbAdmin.getCustomerId))
      /*
       * STEP #2: Retrieve all assets that refer to
       * the configured tenant administrator
       */
      val tbAssets = tbAdmin.getAssets
      /*
       * STEP #3: Reduce the configured stations to those
       * that have not been created already
       */
      val tbAssetNames = tbAssets.map(a => a.getName)
      /*
       * __MOD__ The asset names provided by ThingsBoard
       * refer to the pre-configured station identifiers
       */
      val filteredStations = stations.filter(s => !tbAssetNames.contains(s.id))
      if (filteredStations.isEmpty)
        info(s"All configured production stations exist already.")

      /*
       * STEP #4: Create remaining stations
       * as ThingsBoard assets
       */
      filteredStations.foreach(station => {

        try {

          val tbAsset = new Asset()
          val tbAssetName = station.id

          tbAsset.setName(tbAssetName)
          tbAsset.setType(s"$STATION_NAME")

          tbAsset.setLabel(s"$STATION_NAME: ${station.name}")
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
           *    CREATE STATION ASSET
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
          /*
           * Inform about successful creation of this
           * production station
           */
          info(s"Prod station `${station.name}` successfully created.")

        } catch {
          case t:Throwable =>
            error(s"Creating Prod station `${station.name}` failed: ${t.getLocalizedMessage}")
            success = false
        }

      })
      /*
       * STEP #5: Requests to ThingsBoard are finished,
       * so logout and prepare for next login
       */
      tbAdmin.logout()

    } catch {
      case t:Throwable =>
        error(s"[Creating Prod stations failed: ${t.getLocalizedMessage}")
        success = false
    }
    /*
     * Return flag to indicate whether the creation
     * of production stations failed
     */
    success

  }
  /**
   * This method leverages pre-defined productions rooms
   * (see configuration file) and creates them as ThingsBoard
   * assets without geo spatial coordinates
   */
  def createRoomsIfNotExist():Boolean = {

    var success:Boolean = true
    try {
      /*
       * STEP #1: Retrieve the current TTN devices and group
       * devices by their referenced asset identifier.
       *
       * Note, each TTN device contains an attribute `asset`
       * that links it to one of the rooms.
       */
      val ttnDevices = getTTNDevices
      /*
       * STEP #2: Login to ThingsBoard REST API and retrieve
       * access tokens for this session
       */
      val tbAdmin = new TBAdmin()
      if (!tbAdmin.login())
        throw new Exception("Login to ThingsBoard failed.")

      val tbCustomerId = new CustomerId(
        java.util.UUID.fromString(tbAdmin.getCustomerId))

      /*
       * STEP #3: Retrieve all assets that refer to
       * the configured tenant administrator
       */
      val tbAssets = tbAdmin.getAssets
      /*
       * STEP #4: Reduce the configured rooms to those
       * that have not been created already
       */
      val tbAssetNames = tbAssets.map(a => a.getName)
      /*
       * __MOD__ The asset names provided by ThingsBoard
       * refer to the pre-configured room identifiers
       */
      val filteredRooms = rooms.filter(room => !tbAssetNames.contains(room.id))
      if (filteredRooms.isEmpty)
        info(s"All configured production rooms exist already.")
      /*
       * The list of all successfully created rooms
       */
      val createdRooms = mutable.ArrayBuffer.empty[(String, ProdRoom)]
      /*
       * STEP #5: Create remaining production rooms
       * as ThingsBoard assets
       */
      filteredRooms.foreach(room => {

        try {

          val tbAsset = new Asset()
          val tbRoomName = room.id

          tbAsset.setName(tbRoomName)
          tbAsset.setType(s"$ROOM_NAME")

          tbAsset.setLabel(s"$ROOM_NAME: ${room.name}")
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
           *      CREATE ROOM ASSET
           *
           */
          val tbResponse = tbAdmin.createAsset(tbAsset)
          val tbRoomId = tbAdmin.extractEntityId(tbResponse)

          Thread.sleep(50)
          /* ------------------------------
           *
           *    CREATE DEVICES
           *
           * This implementation leverages the dynamically
           * retrieved TTN devices; note, the link between
           * the current station and `its` TTN devices is
           * defined by the extra TTN attribute `station`.
           */
          val tbDeviceIds = ttnDevices(room.id).map(ttnDevice => {

            val tbDeviceName = buildTBDeviceName(ttnDevice.name, room.id)
            tbAdmin.createTTNDevice(tbCustomerId, datasource, tbDeviceName, ttnDevice)

          }).toList
          /* ------------------------------
           *
           *    CREATE RELATIONS
           */
          tbAdmin.createRelations(datasource, tbRoomId, tbRoomName, tbDeviceIds)
          /*
           * Register the asset identifier of the successfully
           * created production room for subsequent relation to
           * the respective station assets
           */
          val createdRoom = (tbRoomId, room)
          createdRooms += createdRoom
          /*
           * Inform about successful creation of this
           * production room
           */
          info(s"Prod room `${room.name}` successfully created.")

        } catch {
          case t:Throwable =>
            error(s"[Creating Prod room `${room.name}` failed: ${t.getLocalizedMessage}")
            success = false
        }

      })
      /*
       * STEP #6: Build relations between remaining
       * and previously registered stations and these
       * rooms
       */
      if (success) {

        val groupedRooms = createdRooms
          .map {
            case (tbRoomId, room) => (room.station, tbRoomId)
          }
          .groupBy {
            case (station, _) => station
          }
          .map {
            case (station, values) => (station, values.map(v => v._2))
          }

        groupedRooms.foreach { case (tbStationName, tbRoomIds) =>

          try {
            /* ------------------------------
             *
             *        GET ASSET
             *
             */
            val tbResponse = tbAdmin.getAssetByName(tbStationName)
            val tbStationId = tbResponse.getId.getId.toString
            /* ------------------------------
             *
             *    CREATE RELATIONS
             */
            tbAdmin.createRelations(datasource, tbStationId, tbStationName, tbRoomIds.toList)

          } catch {
            case t: Throwable =>
              error(s"[Creating Prod relations for station `$tbStationName` failed: ${t.getLocalizedMessage}")
              success = false
          }
        }

      }
      /*
       * STEP #7: Requests to ThingsBoard are finished,
       * so logout and prepare for next login
       */
      tbAdmin.logout()

    } catch {
      case t:Throwable =>
        error(s"[Creating Prod rooms failed: ${t.getLocalizedMessage}")
        success = false
    }
    /*
     * Return flag to indicate whether the creation
     * of production rooms failed
     */
    success

  }

  /**
   * This method identifies new The ThingsNetwork devices
   * that have been assigned to TTN with an asset identifier,
   * but are not updated in the ThingsBoard server.
   */
  def createDevicesIfNotExist():Boolean = {

    var success:Boolean = true
    try {
      /*
       * STEP #1: Retrieve the current TTN devices and group
       * devices by their referenced asset identifier.
       *
       * Note, each TTN device contains an attribute `asset`
       * that links it to one of the station rooms.
       */
      val ttnDevices = getTTNDevices
      /*
       * STEP #3: Login to ThingsBoard REST API
       * and retrieve access tokens for this session
       */
      val tbAdmin = new TBAdmin()
      if (!tbAdmin.login())
        throw new Exception("Login to ThingsBoard failed.")

      val tbCustomerId = new CustomerId(
        java.util.UUID.fromString(tbAdmin.getCustomerId))

      /*
       * STEP #4: Retrieve all TTN device identifiers
       * that are registered with the Things server
       */
      val ttnDeviceIds = getTTNDeviceIdsExist(tbAdmin)
      /*
       * STEP #5: Restrict to current TTN devices to
       * those that are unknown
       */
      val ttnDevicesUnknown = ttnDevices
        .map{case(asset, devices) =>
          /*
           * Restrict asset specific TTN devices to
           * those that are not listed in the set of
           * known device identifiers
           */
          (asset, devices
            .filter(d => !ttnDeviceIds.contains(d.device_id)))
        }
        .filter{case(_, devices) => devices.nonEmpty}
      /*
       * Step #6: Create all unknown TTN devices and
       * build relations with th referenced stations
       */
      ttnDevicesUnknown.foreach{case(room, devices) =>

        try {
          /* ------------------------------
           *
           *            GET ASSET
           *
           * The `room` value refers to the pre-defined
           * room identifier and this is equal to the
           * respective asset name
           */
          val tbAsset = tbAdmin.getAssetByName(room)
          Thread.sleep(50)
          /* ------------------------------
           *
           *    CREATE DEVICES
           *
           * This implementation leverages the dynamically
           * retrieved TTN devices; note, the link between
           * the current station and `its` TTN devices is
           * defined by the extra TTN attribute `station`.
           */
          val tbDeviceIds = devices.map(ttnDevice => {

            val tbDeviceName = buildTBDeviceName(ttnDevice.name, room)
            tbAdmin.createTTNDevice(tbCustomerId, datasource, tbDeviceName, ttnDevice)

          }).toList
          /* ------------------------------
           *
           *    CREATE RELATIONS
           */
          val tbAssetId = tbAsset.getId.getId.toString
          val tbAssetName = tbAsset.getName

          tbAdmin.createRelations(datasource, tbAssetId, tbAssetName, tbDeviceIds)
          /*
           * Inform about successful creation of this
           * production device
           */
          info(s"TTN device for room `$room` successfully created.")

        } catch {
          case t:Throwable =>
            error(s"Creating TTN device for room `$room` failed: ${t.getLocalizedMessage}")
            success = false
        }
      }
      /*
       * STEP #7: Requests to ThingsBoard are finished,
       * so logout and prepare for next login
       */
      tbAdmin.logout()

    } catch {
      case t:Throwable =>
        error(s"[Creating Prod devices failed: ${t.getLocalizedMessage}")
        success = false
    }
    /*
     * Return flag to indicate whether the creation
     * of production stations failed
     */
    success

  }
  /**
   * This method retrieves all registered devices that
   * refer to the production datasource = `prod`
   */
  private def getTTNDeviceIdsExist(tbAdmin:TBAdmin):Seq[String] = {
    /*
     * STEP #1: Retrieve production specific
     * ThingsBoard devices from the device
     * registry
     */
    val datasource = "prod"
    val deviceEntries = tbAdmin.getDevicesBySource(datasource)
    /*
     * STEP #2: Restrict these devices to their
     * unique TTN device identifier; this value
     * is then used to restrict update requests
     * to those TTN devices that are unknown.
     */
    deviceEntries
      .filter(deviceEntry => deviceEntry.ttnDeviceId.nonEmpty)
      .map(deviceEntry => deviceEntry.ttnDeviceId)

  }

  private def getTTNDevices:Map[String, Seq[TTNDevice]] = {

    val ttnAdmin = new TTNAdmin()
    try {
      /*
       * The ThingsNetwork API request may fail, if the
       * required attributes (assets) are not set for
       * each device.
       *
       * Therefore, requested are embedded in try - catch
       */
      val ttnDevices = ttnAdmin.getDevices
      /*
       * Filter TTN devices to those that reference the
       * pre-defined production rooms
       */
      val roomIds = rooms.map(room => room.id)
      ttnDevices
        .filter(ttnDevice =>
          /*
           * Embedding in a try catch clause ensures
           * that the TTN device has an attribute
           * `asset` assigned.
           *
           * This is introduced for fault tolerance,
           * as we expect that TTN REST API excludes
           * devices that do not have attributes
           * assigned.
           */
          try {
            roomIds.contains(ttnDevice.asset)

          } catch {
            case _:Throwable =>
              error(s"The TTN device `${ttnDevice.name}` has no assigned asset.")
              false
          }
        )
        .groupBy(ttnDevice => ttnDevice.asset)

    } catch {
      case t:Throwable =>
        error(s"The retrieval of TTN devices failed with: ${t.getLocalizedMessage}")
        Map.empty[String, Seq[TTNDevice]]
    }

  }
}
