package de.kp.works.things.slack

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

import com.typesafe.config.{Config, ConfigFactory}

object SlackConf {

  private val path = "slack.conf"
  /**
   * This is the reference to the Slack configuration
   * file that holds all Slack access parameters and
   * metadata of the Pilzdinge app
   */
  private var cfg: Option[Config] = None

  def init(config: Option[String] = None): Boolean = {

    if (cfg.isDefined) true
    else {
      try {

        cfg = if (config.isDefined) {
          /*
           * An external configuration file is provided
           * and must be transformed into a Config
           */
          Option(ConfigFactory.parseString(config.get))

        } else {
          /*
           * The internal reference file is used to
           * extract the required configurations
           */
          Option(ConfigFactory.load(path))

        }
        true

      } catch {
        case _: Throwable =>
          false
      }
    }
  }

  def isInit: Boolean = {
    cfg.isDefined
  }

  def getAppCfg: Config = getCfg("slackApp")

  def getBotCfg: Config = {

    val appCfg = getAppCfg
    appCfg.getConfig("appBot")

  }

  def getCfg(name:String): Config = {

    if (cfg.isEmpty)
      throw new Exception(s"Configuration not initialized.")

    cfg.get.getConfig(name)

  }

}
