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
import de.kp.works.things.tb.TBProducer
import de.kp.works.things.ttn.{TTNAdmin, TTNConsumer, TTNDevice}

import scala.concurrent.ExecutionContextExecutor
import scala.collection.mutable

object ProdConsumer {

  private var instance:Option[ProdConsumer] = None

  def getInstance():ProdConsumer = {

    if (instance.isEmpty)
      instance = Some(new ProdConsumer)

    instance.get

  }

}

class ProdConsumer extends ProdBase {

  private val uuid = java.util.UUID.randomUUID.toString
  /**
   * Akka 2.6 provides a default materializer out of the box, i.e., for Scala
   * an implicit materializer is provided if there is an implicit ActorSystem
   * available. This avoids leaking materializers and simplifies most stream
   * use cases somewhat.
   */
  implicit val prodSystem: ActorSystem = ActorSystem(s"prod-system-$uuid")
  implicit lazy val prodContext: ExecutionContextExecutor = prodSystem.dispatcher

  implicit val prodMaterializer: ActorMaterializer = ActorMaterializer()
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
   * A flag that determines whether this consumer is retrieving
   * values from the ThingsNetwork MQTT broker for the configured
   * production stations and their devices
   */
  private var consuming = true
  /**
   * This consumer is running for ever (until it is stopped
   * explicitly) and leverages the TTN devices and rooms that
   * are known before starting.
   *
   * This implementation requires to restart the Things server
   * after new TTN devices having been registered.
   */
  def start():Unit = {

    extractRooms()
    while(consuming) {}

    stop()

  }

  def stop():Unit = {
    consuming = false
    prodSystem.terminate
  }
  /**
   * This method runs only once (in contrast to
   * `airq` and `owea` use cases; therefore, the
   * actors are created by never released
   */
  def extractRooms():Unit = {
    /*
     * Move through all configured production rooms,
     * create actor for each device associated with a
     * specific room, and start an MQTT client to
     * listen to the TTN sensor readings
     */
    rooms.foreach(room => {
      ttnDevices(room.id).foreach(ttnDevice => {

        val tbDeviceName = buildTBDeviceName(ttnDevice.name, room.id)
        /*
         * Build device specific actor and provide
         * actor to the TTN Consumer (MQTT listener)
         */
        val tbDeviceActor = prodSystem.actorOf(
          Props(new TBProducer()), s"$tbDeviceName-actor")

        val ttnConsumer = new TTNConsumer(tbDeviceName, tbDeviceActor)
        ttnConsumers += tbDeviceName -> ttnConsumer

        ttnConsumer.subscribeAndPublish()

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
