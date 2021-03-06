package de.kp.works.things.devices

import de.kp.works.things.conf.ThingsConf

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

object RepositoryOptions {

  /**
   * The internal configuration is used, if the current
   * configuration is not set here
   */
  if (!ThingsConf.isInit) ThingsConf.init()
  /**
   * Determine the registry folder from the system
   * property `registry.dir`
   */
  private val folder = System.getProperty("registry.dir")
  private val repositoryCfg = ThingsConf.getRepositoryCfg

  def getFolder:String = {

    if (folder == null)
      repositoryCfg.getString("folder")

    else folder

  }

}
