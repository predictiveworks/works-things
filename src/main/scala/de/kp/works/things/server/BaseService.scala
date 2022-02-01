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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import de.kp.works.things.logging.Logging
import de.kp.works.things.server.ssl.SslOptions
import de.kp.works.things.{MappingsConf, ThingsConf}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}

trait BaseService extends Logging {

  private var server:Option[Future[Http.ServerBinding]] = None

  /**
   * Akka 2.6 provides a default materializer out of the box, i.e., for Scala
   * an implicit materializer is provided if there is an implicit ActorSystem
   * available. This avoids leaking materializers and simplifies most stream
   * use cases somewhat.
   */
  implicit val system: ActorSystem = ActorSystem("works-things-service")
  implicit lazy val context: ExecutionContextExecutor = system.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  /**
   * Common timeout for all Akka connection
   */
  implicit val timeout: Timeout = Timeout(15.seconds)

  def buildRoute:Route

  def start(conf:Option[String], mappings:Option[String]):Unit = {

    try {
      /*
       * Initialize the overall configuration
       */
      ThingsConf.init(conf)
      if (!ThingsConf.isInit) {
        throw new Exception(s"Loading configuration failed and service is not started.")
      }
      /*
       * Initialize the attribute mappings
       */
      MappingsConf.init(conf)
      if (!MappingsConf.isInit) {
        throw new Exception(s"Loading mappings failed and service is not started.")
      }

      val routes = buildRoute
      val binding = ThingsConf.getBindingCfg

      val host = binding.getString("host")
      val port = binding.getInt("port")

      val security = ThingsConf.getSecurityCfg
      server =
        if (security.getString("ssl") == "false") {
          Some(Http().bindAndHandle(routes , host, port))

        } else {
          val context = SslOptions.buildConnectionContext(security)

          Http().setDefaultServerHttpContext(context)
          Some(Http().bindAndHandle(routes, host, port, connectionContext = context))
        }

      /* After start processing */
      // onStart()

    } catch {
      case t:Throwable =>
        system.terminate()

        error(t.getLocalizedMessage)
        System.exit(0)
    }

  }

  def onStart():Unit

  def stop():Unit = {

    if (server.isEmpty) {
      system.terminate()

      val now = new java.util.Date().toString
      throw new Exception(s"[ERROR] $now - Service was not launched.")

    }

    server.get
      /*
       * Trigger unbinding from port
       */
      .flatMap(_.unbind())
      /*
       * Shut down application
       */
      .onComplete(_ => {
        system.terminate()
        System.exit(0)
      })

  }

}
