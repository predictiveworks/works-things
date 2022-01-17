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

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.server.Route
import de.kp.works.things.actors.{TBDevice, TBDevices}
import de.kp.works.things.airq.AirQManager
import de.kp.works.things.routes.MobileRoutes
import de.kp.works.things.ttn.TTNManager
import de.kp.works.things.weather.OweaManager

class ThingsService extends BaseService {

  import MobileRoutes._

  override def buildRoute: Route = {

    val actors = buildActors

    val mobileRoutes = new MobileRoutes(actors)
    mobileRoutes.getRoutes

  }

  private def buildActors:Map[String, ActorRef] = {

    Map(
      /*
       * Mobile support
       */
      TB_DEVICE_ACTOR -> system.actorOf(
        Props(new TBDevice()), TB_DEVICE_ACTOR),

      TB_DEVICES_ACTOR -> system.actorOf(
      Props(new TBDevices()), TB_DEVICES_ACTOR)
    )

  }

  override def onStart(): Unit = {

    /* -------------------- DEVICE HANDLING -------------------- */

    /*
     * STEP #1: Create pre-defined Air Quality stations
     * (see configuration file) as ThingsBoard gateway
     * devices if they do not exist already
     */
    var now = new java.util.Date
    println(s"[INFO] ${now.toString} - Create Air Quality stations: started")

    val airqManager = new AirQManager()
    airqManager.createStationsIfNotExist()

    now = new java.util.Date
    println(s"[INFO] ${now.toString} - Create Air Quality stations: finished")
    /*
     * STEP #2: Create pre-defined OpenWeather stations
     * (see configuration file) as ThingsBoard devices
     * if they do not exist already
     */
    now = new java.util.Date
    println(s"[INFO] ${now.toString} - Create Open Weather stations: started")

    val oweaManager = new OweaManager()
    oweaManager.createStationsIfNotExist()

    now = new java.util.Date
    println(s"[INFO] ${now.toString} - Create Open Weather stations: finished")
    /*
     * STEP #3: Create dynamically registered The ThingsNetwork
     * devices as ThingsBoard devices if they do not exist already
     */
    now = new java.util.Date
    println(s"[INFO] ${now.toString} - Create TTN devices: started")

    val ttnManager = new TTNManager()
    ttnManager.createDevicesIfNotExist()

    now = new java.util.Date
    println(s"[INFO] ${now.toString} - Create TTN devices: finished")

    /* -------------------- MQTT HANDLING -------------------- */

    // TODO Start the implemented data sensors

  }
}

