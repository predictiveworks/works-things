package de.kp.works.things.server

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

import de.kp.works.things.airq.{AirqManager, AirqMonitor}
import de.kp.works.things.devices.{DeviceRegistry, RelationRegistry}
import de.kp.works.things.logging.Logging
import de.kp.works.things.owea.{OweaManager, OweaMonitor}
import de.kp.works.things.prod.{ProdManager, ProdMonitor}

import scala.collection.mutable

object ThingsStartup extends Logging {
  /*
   * The list of supported managers. Supported values
   * are `airq`, `owea` and `prod`.
   */
  private val managers = List("airq")
  /*
   * The list of supported consumers. Supported values
   * are `airq`, `owea` and `prod`.
   */
  private val monitors = List("airq")

  def loadRegistries():Boolean = {

    var success:Boolean = true
    try {

      DeviceRegistry.load()
      RelationRegistry.load()

    } catch {
      case t:Throwable =>
        t.printStackTrace()
        error(s"Loading registries failed with: ${t.getLocalizedMessage}")
        success = false
    }

    success
  }

  def activeConsumers():Boolean = {

    var success:Boolean = true
    var startedMonitors = mutable.ArrayBuffer.empty[String]

    monitors.foreach(monitor => {

      try {

        monitor match {
          case "airq" =>
            /*
             * Start consumption of data from
             * pre-defined Air Quality stations
             */
            val airqMonitor = new AirqMonitor()
            if (success) {
              info(s"Consumption of Air Quality stations: started")
              airqMonitor.start()

              startedMonitors += "airq"
            }

          case "owea" =>
            /*
             * Start consumption of data from
             * pre-defined Weather stations
             */
            val oweaMonitor = new OweaMonitor()
            if (success) {
              info(s"Consumption of Weather stations: started")
              oweaMonitor.start()

              startedMonitors += "owea"
            }

          case "prod" =>
            /*
              * Start consumption of data from
              * pre-defined Production stations
              */
            val prodMonitor = new ProdMonitor()
            if (success) {
              info(s"Consumption of Production stations: started")
              prodMonitor.start()

              startedMonitors += "prod"
            }

          case _ =>
            throw new Exception(s"The monitor `$monitor` is not supported.")
        }

      } catch {
        case t:Throwable =>
          error(s"Activating consumers failed with: ${t.getLocalizedMessage}")
          success = false
      }

    })

    info(s"The following monitors have been started: ${startedMonitors.mkString(", ")}")
    success

  }

  /**
   * This method is invoked during the startup
   * phase and creates pre-defined assets and
   * associated devices.
   */
  def createAssetsIfNotExist():Boolean = {

    var success:Boolean = true
    var startedManagers = mutable.ArrayBuffer.empty[String]

    managers.foreach(manager => {

      try {

        manager match {
          case "airq" =>
            /*
             * Create pre-defined Air Quality stations and associated
             * sensors (see configuration file) as ThingsBoard assets
             * and  devices if they do not exist already
             */
            val airqManager = new AirqManager()
            if (success) {
              info(s"Create Air Quality stations: started")
              success = airqManager.createStationsIfNotExist()

              if (success) {
                info(s"Create Air Quality stations: finished")
                startedManagers += "airq"
              }
            }
          case "owea" =>
            /*
             * Create pre-defined OpenWeather stations and associated
             * sensors (see configuration file) as ThingsBoard assets
             * and devices if they do not exist already
             */
            val oweaManager = new OweaManager()
            if (success) {
              info(s"Create Open Weather stations: started")
              success = oweaManager.createStationsIfNotExist()

              if (success) {
                info(s"Create Open Weather stations: finished")
                startedManagers += "owea"
              }
            }
          case "prod" =>
            /*
             * Create pre-defined Production stations and associated
             * rooms (see configuration file) and dynamic TTN devices
             * as ThingsBoard assets and devices if they do not exist
             * already
             */
            val prodManager = new ProdManager()
            if (success) {
              info(s"Create Production stations: started")
              success = prodManager.createStationsIfNotExist()

              if (success) info(s"Create Production stations: finished")
            }
            if (success) {
              info(s"Create Production rooms: started")
              success = prodManager.createRoomsIfNotExist()

              if (success) info(s"Create Production rooms: finished")
            }
            if (success) {
              info(s"Create Production devices: started")
              success = prodManager.createDevicesIfNotExist()

              if (success) info(s"Create Production devices: finished")
            }
            if (success)
              startedManagers += "prod"
          case _ =>
            throw new Exception(s"The manager `$manager` is not supported.")
        }

      } catch {
        case t:Throwable =>
          error(s"Running managers failed with: ${t.getLocalizedMessage}")
          success = false
      }

    })

    info(s"The following managers have been started: ${startedManagers.mkString(", ")}")
    /*
     * Return flag to indicate that the creation of
     * the pre-defined assets and associated devices
     * was successful
     */
    success

  }
}
