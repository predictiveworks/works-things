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

case class OrsPositionReq(
  /*
   * The unique device identifier of the IoT device,
   * the current geospatial location is requested for.
   */
  device:String,
  /*
   * Step indicates whether this is an initial request
   * with `step` is 0 or a follow-on request with `step`
   * greater than zero
   */
  step:Int,
  /*
   * The secret is used to restrict to authorized
   * requests
   */
  secret:String
)

case class OrsRouteReq(
  /*
   * The profile used to compute the OSM route
   * between start & end point
   */
  profile:String,
  /*
   * The starting point latitude & longitude
   */
  startLat:Double,
  startLon:Double,
  /*
   * The end point latitude & longitude
   */
  endLat:Double,
  endLon:Double,
  /*
   * The secret is used to restrict to authorized
   * requests
   */
  secret:String
)
