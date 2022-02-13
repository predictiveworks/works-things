package de.kp.works.things.map

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


import de.kp.works.things.conf.GeospatialConf

object MapOptions {
  /**
   * The internal configuration is used, if the current
   * configuration is not set here
   */
  if (!GeospatialConf.isInit) GeospatialConf.init()
  private val orsCfg = GeospatialConf.getOrsCfg

  def getOrsToken:String = orsCfg.getString("authToken")

  def getOrsUrl:String = orsCfg.getString("serverUrl")

}