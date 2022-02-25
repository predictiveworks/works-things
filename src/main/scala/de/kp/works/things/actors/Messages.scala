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

object Messages {

  def invalidJson(): String =
    s"Request did not contain valid JSON."

  def unauthorizedReq(): String =
    s"Unauthorized request detected."

  def failedAssetsReq(t:Throwable):String =
    s"Retrieval of assets within an administrative boundary failed with ${t.getLocalizedMessage}"

  def failedBoundaryReq(t:Throwable):String =
    s"Retrieval of administrative boundary failed with ${t.getLocalizedMessage}"

  def failedDetailReq(t:Throwable):String =
    s"Retrieval of sensor time series failed with ${t.getLocalizedMessage}"

  def failedDevicesReq(t:Throwable):String =
    s"Retrieval of devices failed with ${t.getLocalizedMessage}"

  def failedPositionReq(t:Throwable):String =
    s"Retrieval of geospatial position failed with ${t.getLocalizedMessage}"

  def failedStationReq(t:Throwable):String =
    s"Retrieval of latest station data failed with ${t.getLocalizedMessage}"

}
