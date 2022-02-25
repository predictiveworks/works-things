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
import de.kp.works.things.actors._
import de.kp.works.things.routes.MobileRoutes

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
       * Air quality (mobile) support
       */
      AIRQ_DETAIL_ACTOR -> system.actorOf(
        Props(new AirqDetail()), AIRQ_DETAIL_ACTOR),

      AIRQ_STATION_ACTOR -> system.actorOf(
        Props(new AirqStation()), AIRQ_STATION_ACTOR),

      AIRQ_STATIONS_ACTOR -> system.actorOf(
        Props(new AirqStations()), AIRQ_STATIONS_ACTOR),
      /*
       * Geospatial (mobile) support
       */
      ORS_ASSETS_ACTOR -> system.actorOf(
        Props(new OrsAssets()), ORS_ASSETS_ACTOR),

      ORS_BOUNDARY_ACTOR -> system.actorOf(
        Props(new OrsBoundary()), ORS_BOUNDARY_ACTOR),

      ORS_POSITION_ACTOR -> system.actorOf(
        Props(new OrsPosition()), ORS_POSITION_ACTOR),

      ORS_ROUTE_ACTOR -> system.actorOf(
        Props(new OrsRoute()), ORS_ROUTE_ACTOR),
      /*
       * Open weather (mobile) support
       */
      OWEA_DETAIL_ACTOR -> system.actorOf(
        Props(new OweaDetail()), OWEA_DETAIL_ACTOR),

      OWEA_STATION_ACTOR -> system.actorOf(
        Props(new OweaStation()), OWEA_STATION_ACTOR),

      OWEA_STATIONS_ACTOR -> system.actorOf(
        Props(new OweaStations()), OWEA_STATIONS_ACTOR),
      /*
       * Production (mobile) support
       */
      PROD_DETAIL_ACTOR -> system.actorOf(
        Props(new ProdDetail()), PROD_DETAIL_ACTOR),

      PROD_STATION_ACTOR -> system.actorOf(
        Props(new ProdStation()), PROD_STATION_ACTOR),

      PROD_STATIONS_ACTOR -> system.actorOf(
        Props(new ProdStations()),PROD_STATIONS_ACTOR),
      /*
       * ThingsBoard device support
       */
      TB_DEVICE_ACTOR -> system.actorOf(
        Props(new TBDevice()), TB_DEVICE_ACTOR),

      TB_DEVICES_ACTOR -> system.actorOf(
      Props(new TBDevices()), TB_DEVICES_ACTOR),
      /*
       * The ThingsNetwork device support
       */
      TTN_DEVICES_ACTOR -> system.actorOf(
      Props(new TTNDevices()), TTN_DEVICES_ACTOR)
    )

  }

  override def onStart(): Unit = {
    /*
     * STEP #1: Load device & relation registry
     * to provide registered knowledge
     */
    var success = ThingsStartup.loadRegistries()
    if (!success) {
      throw new Exception(s"Loading of registries failed.")
    }
    /*
     * STEP #2: Asset & device handling creates
     * pre-defined stations, rooms and devices
     * if they do not exist already
     */
    success = ThingsStartup.createAssetsIfNotExist()
    if (!success) {
      throw new Exception(s"Creation of ThingsBoard assets and devices failed.")
    }
    /*
     * STEP #3: Start all implemented data sensors
     * to retrieve device specific real-time data
     * and publish to ThingsBoard
     */
    success = ThingsStartup.activeConsumers()
    if (!success) {
      throw new Exception(s"Activation of data consumers failed.")
    }
    /*
     * STEP #4: Start the Slack Bot to inform about
     * the measurements at 6AM and 6PM
     */
    success = ThingsStartup.startSlackBot()
    if (!success) {
      throw new Exception(s"Starting Slack Bot failed.")
    }

  }
}

