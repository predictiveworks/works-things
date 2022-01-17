package de.kp.works.things.routes

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

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import de.kp.works.things.actors.BaseActor.Response

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.{Failure, Success}

object MobileRoutes {

  val TB_DEVICE_ACTOR  = "tb_device_actor"
  val TB_DEVICES_ACTOR = "tb_devices_actor"

}

class MobileRoutes(actors:Map[String, ActorRef])(implicit system: ActorSystem) {

  implicit lazy val context: ExecutionContextExecutor = system.dispatcher
  /**
   * Common timeout for all Akka connections
   */
  implicit val timeout: Timeout = Timeout(15.seconds)

  import MobileRoutes._

  def getRoutes:Route = {
    getTBDevice ~ getTBDevices
  }

  private def getTBDevice:Route = routePost("v1/mobile/device", actors(TB_DEVICE_ACTOR))

  private def getTBDevices:Route = routePost("v1/mobile/devices", actors(TB_DEVICES_ACTOR))

  /*******************************
   *
   * HELPER METHODS
   *
   */
  private def routePost(url:String, actor:ActorRef):Route = {
    val matcher = separateOnSlashes(url)
    path(matcher) {
      post {
        extract(actor)
      }
    }
  }

  private def extract(actor:ActorRef) = {
    extractRequest { request =>
      complete {
        /*
         * The Http(s) request is sent to the respective
         * actor and the actor' response is sent to the
         * requester as response.
         */
        val future = actor ? request
        Await.result(future, timeout.duration) match {
          case Response(Failure(e)) =>
            val message = e.getMessage
            jsonResponse(message)
          case Response(Success(answer)) =>
            val message = answer.asInstanceOf[String]
            jsonResponse(message)
        }
      }
    }
  }

  private def jsonResponse(message:String) = {

    HttpResponse(
      status=StatusCodes.OK,
      entity = ByteString(message),
      protocol = HttpProtocols.`HTTP/1.1`)

  }

}
