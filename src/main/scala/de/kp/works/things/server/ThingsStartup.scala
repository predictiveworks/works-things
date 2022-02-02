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
import de.kp.works.things.logging.Logging
import de.kp.works.things.owea.{OweaManager, OweaMonitor}
import de.kp.works.things.prod.{ProdManager, ProdMonitor}

object ThingsStartup extends Logging {

  def activeConsumers():Boolean = {

    var success:Boolean = true
    try {
      /*
       * STEP #1: Start consumption of data from
       * pre-defined Air Quality stations
       */
      info(s"Consumption of Air Quality stations: started")

      val airqMonitor = new AirqMonitor()
      airqMonitor.start()
      /*
       * STEP #2: Start consumption of data from
       * pre-defined Weather stations
       */
      info(s"Consumption of Weather stations: started")

      val oweaMonitor = new OweaMonitor()
      oweaMonitor.start()
      /*
       * STEP #3: Start consumption of data from
       * pre-defined Production stations
       */
      info(s"Consumption of Production stations: started")

      val prodMonitor = new ProdMonitor()
      prodMonitor.start()

    } catch {
      case t:Throwable =>
        error(s"Activating consumers failed with: ${t.getLocalizedMessage}")
        success = false
    }

    success

  }

  /**
   * This method is invoked during the startup
   * phase and creates pre-defined assets and
   * associated devices.
   */
  def createAssetsIfNotExist():Boolean = {

    var success:Boolean = true
    /*
     * STEP #1: Create pre-defined Air Quality stations
     * and associated sensors (see configuration file)
     * as ThingsBoard assets and  devices if they do not
     * exist already
     */
    info(s"Create Air Quality stations: started")

    val airqManager = new AirqManager()
    success = airqManager.createStationsIfNotExist()

    if (success) info(s"Create Air Quality stations: finished")
    else {
      /*
       * In case of a startup failure, this method is
       * aborted and the Things service is informed
       */
      return success
    }
    /*
     * STEP #2: Create pre-defined OpenWeather stations
     * and associated sensors (see configuration file)
     * as ThingsBoard assets and devices if they do not
     * exist already
     */
    info(s"Create Open Weather stations: started")

    val oweaManager = new OweaManager()
    success = oweaManager.createStationsIfNotExist()

    if (success) info(s"Create Open Weather stations: finished")
    else {
      /*
       * In case of a startup failure, this method is
       * aborted and the Things service is informed
       */
      return success
    }
    /*
     * STEP #3: Create pre-defined Production stations
     * (see configuration file as ThingsBoard assets
     * if they do not exist already
     */
    info(s"Create Production stations: started")

    val prodManager = new ProdManager()
    success = prodManager.createStationsIfNotExist()

    if (success) info(s"Create Production stations: finished")
    else {
      /*
       * In case of a startup failure, this method is
       * aborted and the Things service is informed
       */
      return success
    }
    /*
     * STEP #4: Create pre-defined Production rooms
     * (see configuration file as ThingsBoard assets
     * if they do not exist already, connect to the
     * created Production stations, and, create
     * associated dynamic TTN devices
     */
    info(s"Create Production rooms: started")

    success = prodManager.createRoomsIfNotExist()

    if (success) info(s"Create Production rooms: finished")
    else {
      /*
       * In case of a startup failure, this method is
       * aborted and the Things service is informed
       */
      return success
    }
    /*
     * STEP #5: Create TTN devices that are assigned
     * to the previously generated Production rooms
     */
    info(s"Create Production devices: started")

    success = prodManager.createDevicesIfNotExist()

    if (success) info(s"Create Production devices: finished")
    else {
      /*
       * In case of a startup failure, this method is
       * aborted and the Things service is informed
       */
      return success
    }
    /*
     * Return flag to indicate that the creation of
     * the pre-defined assets and associated devices
     * was successful
     */
    success

  }
}
