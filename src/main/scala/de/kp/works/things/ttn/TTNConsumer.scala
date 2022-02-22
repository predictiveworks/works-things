package de.kp.works.things.ttn

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

import akka.actor.ActorRef
import com.google.gson.{JsonElement, JsonParser}
import de.kp.works.things.devices.{DeviceEntry, DeviceRegistry}
import de.kp.works.things.logging.Logging
import de.kp.works.things.tb.{TBColumn, TBJob, TBRecord, TBTimeseries}
import org.eclipse.paho.client.mqttv3.{IMqttDeliveryToken, MqttCallback, MqttClient, MqttMessage}

import scala.collection.JavaConversions._

/**
 * The [TTNConsumer] subscribes to a certain topic
 * of TheThingsNetwork MQTT broker and sends the
 * transformed result to the ThingsBoard server
 */
class TTNConsumer(tbDeviceName:String, tbDeviceActor: ActorRef) extends Logging {

  private val tbDeviceEntry = DeviceRegistry.get(tbDeviceName)

  private val mqttClient: Option[MqttClient] = buildMqttClient

  private def buildMqttClient:Option[MqttClient] = {

    val brokerUrl = TTNOptions.getBrokerUrl
    val clientId  = TTNOptions.getClientId

    val persistence = TTNOptions.getPersistence
    Some(new MqttClient(brokerUrl, clientId, persistence))

  }

  def subscribeAndPublish():Unit = {
    /*
     * Callback automatically triggers as and when new message
     * arrives on specified topic
     */
    val callback: MqttCallback = new MqttCallback() {

      override def messageArrived(topic: String, message: MqttMessage) {
         publish(message)
      }

      override def deliveryComplete(token: IMqttDeliveryToken) {}

      override def connectionLost(cause: Throwable) {
        restart(cause)
      }

    }
    /*
     * Make sure that the Mqtt client is defined
     * to connect to the TTN broker
     */
    if (mqttClient.isEmpty) buildMqttClient

    if (tbDeviceEntry.isEmpty) {
      error(s"No device registry entry found for `$tbDeviceName`.")

    }
    else {
      /*
       * Set up callback for MqttClient. This needs to happen before
       * connecting or subscribing, otherwise messages may be lost
       */
      mqttClient.get.setCallback(callback)

      val mqttOptions = TTNOptions.getMqttOptions
      mqttClient.get.connect(mqttOptions)

      if (!mqttClient.get.isConnected) {
        error(s"TTN Consumer could not connect to The ThingsNetwork.")

      }
      else {
        info(s"TTN Consumer is connected to The ThingsNetwork.")

        val mqttTopic = tbDeviceEntry.get.ttnMqttTopic
        mqttClient.get.subscribe(mqttTopic, TTNOptions.getQos)

      }

    }

  }

  def restart(t:Throwable): Unit = {
    warn(s"TTN Consumer restart due to: ${t.getLocalizedMessage}")
    subscribeAndPublish()
  }

  def unsubscribe():Unit = {

    if (mqttClient.isEmpty) return
    mqttClient.get.disconnect()

    info(s"TTN Consumer is disconnected from The ThingsNetwork.")

  }
  /**
   * This method publishes TTN telemetry data to the ThingsBoard
   * server; at this stage, the original (client) attribute names
   * are used.
   *
   * This approach ensures that TTN server and TB server are in
   * sync with respect to the device attributes
   */
  def publish(mqttMessage:MqttMessage):Unit = {

    try {

      val payload = new String(mqttMessage.getPayload)
      val json = JsonParser.parseString(payload)
      /*
       * Extract uplink message and associated
       * decoded payload
       */
      val now = new java.util.Date()
      val messageObj = json.getAsJsonObject

      val decodedPayload = TTNRegistry.transform(messageObj)
      println(s"----- decoded ${now.toString} -----")
      println(decodedPayload)

      if (decodedPayload.nonEmpty) {
        /*
         * Transform payload into [TBColumns]
         * and build associated [TBJob]
         */
        val tbColumns = decodedPayload.get.entrySet()
          /*
           * Restrict attributes to numeric
           * attributes
           */
          .filter(entry => {

            val attrValue = entry.getValue

            if (!attrValue.isJsonPrimitive) false
            else {
              val primitive = attrValue.getAsJsonPrimitive
              if (!primitive.isNumber) false else true
            }

          })
          .map(entry => {

            val attrName = entry.getKey
            val attrValue = getDouble(entry.getValue)

            TBColumn(attrName, attrValue)

          }).toSeq

        val ts = System.currentTimeMillis
        val tbRecords = Seq(TBRecord(ts, tbColumns))

        val tbTimeseries = TBTimeseries(tbRecords)

        val tbJob = TBJob(tbDeviceName, tbTimeseries, actorStop = false)
        /*
         * Send [TBJob] request to the provided
         * device actor
         */
        tbDeviceActor ! tbJob

      } else {
        warn(s"Unknown TTN payload detected: $payload")
      }

    } catch {
      case t:Throwable =>
        error(s"Consuming MQTT message $mqttMessage failed: ${t.getLocalizedMessage}")
    }

  }

  private def getDouble(json:JsonElement):Double = {

    val number = json.getAsJsonPrimitive.getAsNumber
    try {
      number.doubleValue

    } catch {
      case _:Throwable =>
        try {
          number.longValue()

        } catch {
          case _:Throwable => number.intValue()
        }
    }

  }
}
