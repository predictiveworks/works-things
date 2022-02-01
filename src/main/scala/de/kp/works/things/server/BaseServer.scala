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

import de.kp.works.things.logging.Logging

trait BaseServer extends Logging {

  protected var programName:String
  protected var programDesc:String
  /**
   * The name of the external file that contains all
   * configuration specific parameters for this Things
   * server application
   */
  protected var configFile:Option[String]
  /**
   * The name of the external file that contains all
   * (client) attribute mappings between backend and
   * frontend attribute names.
   *
   * Note: This is an important approach to harmonize
   * frontend attribute names for different TTN device
   * attributes.
   */
  protected var mappingsFile:Option[String]

  def main(args:Array[String]):Unit = {

    try {
      launch(args)

    } catch {
      case t: Throwable =>

        error(s"$programName cannot be started: " + t.getMessage)
        /*
         * Sleep for 10 seconds so that one may see error messages
         * in Yarn clusters where logs are not stored.
         */
        Thread.sleep(10000)
        sys.exit(1)

    }

  }

  protected def launch(args:Array[String]):Unit

  protected def start(args:Array[String], service:BaseService):Unit = {

    val line = s"------------------------------------------------"
    info(line)

    val cfg = loadCfgAsString
    val mappings = loadMappingsAsString

    service.start(cfg, mappings)

    info(s"$programName service started.")
    info(line)

  }

  private def loadCfgAsString:Option[String] = {

    if (configFile.isEmpty) {
      info(s"Launch $programName with internal configuration.")
      None

    } else {
      info(s"Launch $programName with external configuration.")

      val source = scala.io.Source.fromFile(new java.io.File(configFile.get))
      val config = source.getLines.mkString("\n")

      source.close
      Some(config)

    }

  }

  private def loadMappingsAsString:Option[String] = {

    if (configFile.isEmpty) {
      info(s"Launch $programName with internal mappings.")
      None

    } else {
      info(s"Launch $programName with external mappings.")

      val source = scala.io.Source.fromFile(new java.io.File(mappingsFile.get))
      val mappings = source.getLines.mkString("\n")

      source.close
      Some(mappings)

    }

  }
}
