package de.kp.works.things.prod

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

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import de.kp.works.things.logging.Logging
import de.kp.works.things.tb.TBProducer
import de.kp.works.things.ttn.{TTNAdmin, TTNConsumer, TTNDevice}

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.ExecutionContextExecutor
import scala.collection.mutable

class ProdMonitor(numThreads:Int = 1) extends Logging {

  private val uuid = java.util.UUID.randomUUID.toString
  /**
   * Akka 2.6 provides a default materializer out of the box, i.e., for Scala
   * an implicit materializer is provided if there is an implicit ActorSystem
   * available. This avoids leaking materializers and simplifies most stream
   * use cases somewhat.
   */
  implicit val system: ActorSystem = ActorSystem(s"prod-monitor-$uuid")
  implicit lazy val context: ExecutionContextExecutor = system.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  private var executorService:ExecutorService = _
  private val consumer = new ProdConsumer(system)

  def start():Unit = {

    val worker = new Runnable {

      override def run(): Unit = {
        info(s"Prod Consumer started.")
        consumer.extractRooms()
      }
    }

    try {

      executorService = Executors.newFixedThreadPool(numThreads)
      executorService.execute(worker)

    } catch {
      case t:Exception =>
        error(s"Prod Monitor failed with: ${t.getLocalizedMessage}")
        stop()
    }

  }

  def stop():Unit = {

    executorService.shutdown()
    executorService.shutdownNow()

    system.terminate()
  }

}

class ProdConsumer(prodSystem:ActorSystem) extends ProdBase with Logging {
  /**
   * The production consumer listens to TTN devices that are associated
   * to assets that reference production rooms.
   *
   * This approach is different from Airq & Owea consumers
   */
  private val rooms = ProdOptions.getRooms
  private val ttnDevices = getTTNDevices

  private val ttnConsumers = mutable.HashMap.empty[String, TTNConsumer]
  /**
   * This method runs only once (in contrast to
   * `airq` and `owea` use cases; therefore, the
   * actors are created by never released
   */
  def extractRooms():Unit = {

    info(s"Prod consumer: Extract data from [ThingsNetwork]")
    /*
     * Move through all configured production rooms,
     * create actor for each device associated with a
     * specific room, and start an MQTT client to
     * listen to the TTN sensor readings
     */
    rooms.foreach(room => {
      ttnDevices(room.id).foreach(ttnDevice => {

        val tbDeviceName = buildTBDeviceName(ttnDevice.name, room.id)
        try {
          /*
           * Build device specific actor and provide
           * actor to the TTN Consumer (MQTT listener)
           */
          val tbDeviceActor = prodSystem.actorOf(
            Props(new TBProducer()), s"$tbDeviceName-actor")

          val ttnConsumer = new TTNConsumer(tbDeviceName, tbDeviceActor)
          ttnConsumers += tbDeviceName -> ttnConsumer

          ttnConsumer.subscribeAndPublish()

        } catch {
          case t:Throwable =>
            error(s"Creating MQTT listener for `$tbDeviceName` failed: ${t.getLocalizedMessage}")
        }

      })
    })

  }

  private def getTTNDevices:Map[String, Seq[TTNDevice]] = {

    val ttnAdmin = new TTNAdmin()
    try {

      val ttnDevices = ttnAdmin.getDevices
      ttnDevices.groupBy(ttnDevice => ttnDevice.asset)

    } catch {
      case _:Throwable => Map.empty[String, Seq[TTNDevice]]
    }

  }

}
