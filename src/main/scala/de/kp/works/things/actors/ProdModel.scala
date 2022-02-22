package de.kp.works.things.actors

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

case class ProdDetailReq(
  /*
   * The identifier `id` refers to the unique identifier
   * of a ThingsBoard device (`deviceId`)
   */
  id: String,
  /*
   * The time interval used to request telemetry data;
   * supported values are 1d, 3d, 10d, and 30d
   */
  interval:String,
  /*
   * The name of the sensor (co, co2, no, etc.)
   */
  sensor: String,
  /*
   * The secret is used to restrict to authorized
   * requests
   */
  secret:String
)

case class ProdStationReq(
  /*
   * The identifier `id` refers to the pre-defined
   * (see configuration) unique identifier of the
   * UI station
   */
  id: String,
  /*
   * The parameter `room` refers to the pre-defined
   * (see configuration) name of the UI station rom
   */
  room: String,
  /*
   * The secret is used to restrict to authorized
   * requests
   */
  secret:String
)

case class ProdStationsReq(
  /*
   * The identifier `id` refers to the dynamically
   * selected device
   */
  id: String,
  /*
   * The name of the sensor (co, co2, no, etc.)
   */
  sensor: String,
  /*
   * The secret is used to restrict to authorized
   * requests
   */
  secret:String
)