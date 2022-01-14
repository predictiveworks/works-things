package de.kp.works.things.server

/*
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

trait BaseServer {

  protected var programName:String
  protected var programDesc:String

  protected var configFile:Option[String]

  def main(args:Array[String]):Unit = {

    try {
      launch(args)

    } catch {
      case t: Throwable =>

        println("[ERROR] ------------------------------------------------")
        println(s"[ERROR] $programName cannot be started: " + t.getMessage)
        println("[ERROR] ------------------------------------------------")

        t.printStackTrace()
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

    val cfg = if (configFile.isEmpty) {

      println("[INFO] ------------------------------------------------")
      println(s"[INFO] Launch $programName with internal configuration.")
      println("[INFO] ------------------------------------------------")

      None

    } else {

      println("[INFO] ------------------------------------------------")
      println(s"[INFO] Launch $programName with external configuration.")
      println("[INFO] ------------------------------------------------")

      val source = scala.io.Source.fromFile(new java.io.File(configFile.get))
      val config = source.getLines.mkString("\n")

      source.close
      Some(config)
    }

    service.start(cfg)

    println("[INFO] ------------------------------------------------")
    println(s"[INFO] $programName service started.")
    println("[INFO] ------------------------------------------------")

  }
}
