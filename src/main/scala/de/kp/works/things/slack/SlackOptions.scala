package de.kp.works.things.slack

import de.kp.works.things.conf.SlackConf

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

object SlackOptions {
  /**
   * The internal configuration is used, if the current
   * configuration is not set here
   */
  if (!SlackConf.isInit) SlackConf.init()
  private val botCfg = SlackConf.getBotCfg

  def getChannel:String = botCfg.getString("channel")

  def getOAuthToken:String = botCfg.getString("token")

}
