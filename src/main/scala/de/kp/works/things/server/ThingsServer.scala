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

object ThingsServer extends BaseServer {

  override var programName: String = "ThingsServer"
  override var programDesc: String = "Connect multiple data sources to a ThingsBoard server."
  /*
   * In case of a deployed `Things` server, the file system
   * path to the configuration folder is provided as system
   * property `config.dir`
   */
  private val configDir = System.getProperty("config.dir")
  private val cFile =
    if (configDir != null) Some(s"$configDir/reference.conf") else None

  private val mFile =
    if (configDir != null) Some(s"$configDir/mappings.conf") else None

  override protected var configFile: Option[String] = cFile
  /**
   * The name of the external file that contains all
   * (client) attribute mappings between backend and
   * frontend attribute names.
   *
   * Note: This is an important approach to harmonize
   * frontend attribute names for different TTN device
   * attributes.
   */
  override protected var mappingsFile: Option[String] = mFile

  override def launch(args: Array[String]): Unit = {

    val service = new ThingsService()
    start(args, service)

  }
}
