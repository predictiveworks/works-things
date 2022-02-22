package de.kp.works.things.tb

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

import akka.actor.Actor
import com.google.gson.JsonObject
import de.kp.works.things.devices.DeviceRegistry
import de.kp.works.things.logging.Logging
import org.eclipse.paho.client.mqttv3.{MqttClient, MqttException, MqttMessage}

case class TBColumn(name:String, value:Double)

case class TBRecord(ts:Long, columns:Seq[TBColumn])

case class TBTimeseries(records:Seq[TBRecord])

case class TBJob(deviceName:String, deviceSeries:TBTimeseries, actorStop:Boolean = true)
/**
 * The [TBProducer] is responsible for publishing MQTT messages
 * to the ThingsBoard MQTT broker that refer to a certain topic
 * or device. The current implementation defines this producer
 * as an actor that
 *
 * (1) connects, (2) publishes and (3) disconnects to the broker.
 *
 * Note, the current time behavior of the covered devices is
 * sending in 10min (environment), 15min (weather), 30min (air
 * quality) and 60min (CO2)
 */
class TBProducer extends Actor with Logging {

  override def receive: Receive = {

    case TBJob(deviceName, deviceSeries, actorStop) =>
      /*
       * STEP #1: Retrieve access parameters for the provided
       * device from the device registry
       */
      val deviceEntry = DeviceRegistry.get(deviceName)
      if (deviceEntry.isEmpty) {

        warn(s"Device `$deviceName` is not registered in the device registry.")
        /*
         * Stop this actor as each actor is assigned
         * to a certain device
         */
        if (actorStop) context.stop(self)

      }
      else {
        /*
         * STEP #2: Set the MQTT topic that is assigned
         * to this device
         */
        val tbMqttTopic = deviceEntry.get.tbMqttTopic
        /*
         * STEP #3: Connect to ThingsBoard via MQTT using
         * the provided topic and device token
         */
        val tbDeviceToken = deviceEntry.get.tbDeviceToken
        val tbMqttClient = start(tbDeviceToken)
        /*
         * STEP #4: Check whether the [TBProducer] is connected
         * to the ThingsBoard MQTT broker
         */
        if (tbMqttClient == null) {

          warn(s"Device `$deviceName` could not be connected to ThingsBoard broker.")
          /*
           * Stop this actor as each actor is assigned
           * to a certain device
           */
          if (actorStop) context.stop(self)

        }
        else {
          /*
           * STEP #5: Publish all provided records to the
           * ThingsBoard MQTT broker
           */
          deviceSeries.records.foreach(record => {
            /*
             * Every record is transformed into
             * a JSON payload
             */
            val tbPayload = new JsonObject
            tbPayload.addProperty("ts", record.ts)

            val values = new JsonObject

            record.columns.foreach(column => {
              values.addProperty(column.name, column.value)
            })

            tbPayload.add("values", values)
            /*
             * The transformed record is sent to
             * the ThingsBoard MQTT broker
             */
            publish(tbMqttClient, tbMqttTopic, tbPayload.toString)

          })
          /*
           * STEP #6: Disconnect from the ThingsBoard MQTT
           * broker and destroy this actor
           */
          stop(tbMqttClient)
          if (actorStop) context.stop(self)

        }
      }
    case _ =>
      error(s"Unknown request for [TBProducer] detected.")
  }

  private def buildMqttClient:MqttClient = {

    val brokerUrl = TBOptions.getBrokerUrl
    val clientId  = TBOptions.getClientId

    val persistence = TBOptions.getPersistence
    new MqttClient(brokerUrl, clientId, persistence)

  }

  /**
   * The `deviceToken` is either a registered ThingsBoard
   * `device` or a `gateway` token.
   */
  private def start(deviceToken:String):MqttClient = {

    val mqttClient = buildMqttClient

    val mqttOptions = TBOptions.getMqttOptions(deviceToken)
    mqttClient.connect(mqttOptions)

    if (mqttClient.isConnected) {
      info(s"TB Producer is connected to ThingsBoard.")
      mqttClient

    }
    else {
      null

    }

  }

  private def stop(mqttClient:MqttClient):Unit = {

    mqttClient.disconnect()
    info(s"TB Producer is disconnected from ThingsBoard.")

  }

  /**
   * This is the basic method to send a certain
   * MQTT message in a ThingsBoard compliant
   * format to the server
   */
  private def publish(mqttClient:MqttClient, mqttTopic:String, mqttPayload:String):Unit = {

    try {

      val mqttMessage = new MqttMessage(mqttPayload.getBytes("UTF-8"))
      mqttMessage.setQos(TBOptions.getQos)

      mqttClient.publish(mqttTopic, mqttMessage)

    } catch {
      case t:MqttException =>

        val reasonCode = t.getReasonCode
        val cause = t.getCause

        val message = t.getMessage
        error(s"Mqtt publishing failed: Reason=$reasonCode, Cause=$cause, Message=$message")

    }

  }
}
