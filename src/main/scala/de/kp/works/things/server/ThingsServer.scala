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

/**
 * The [ThingsServer] is designed to connect to 3 different
 * data sources, EU Air Quality service, OpenWeather and The
 * Things Stack. The data are read either on a scheduled basis
 * or on demand as MQTT listening.
 *
 * This approach can be extended to other (real-time) data
 * sources as well like stock exchange rates.
 *
 * The current data destination or sink is the ThingsBoard
 * MQTT broker (community edition) that persists received
 * telemetry data in a Postgres database.
 */
object ThingsServer extends BaseServer {

  override var programName: String = "ThingsServer"
  override var programDesc: String = "Connect multiple data sources to a ThingsBoard server."

  override def launch(args: Array[String]): Unit = {

    val service = new ThingsService()
    start(args, service)

  }
}
