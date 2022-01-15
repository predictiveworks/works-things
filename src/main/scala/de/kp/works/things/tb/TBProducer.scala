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

import org.eclipse.paho.client.mqttv3.{MqttClient, MqttException, MqttMessage}

class DeviceProducer extends TBProducer {

  setTBTopic(TBOptions.DEVICE_TELEMETRY_TOPIC)

}
/**
 * The [TBProducer] is responsible for writing MQTT
 * message that refer to a certain device to the
 * ThingsBoard server
 */
class TBProducer {

  private val mqttClient: Option[MqttClient] = buildMqttClient
  private var connected:Boolean = false

  private var mqttTopic:Option[String] = None

  private var verbose:Option[Boolean] = Some(true)

  def setTBTopic(topic:String):TBProducer = {
    this.mqttTopic = Some(topic)
    this
  }

  def setVerbose(verbose:Boolean):TBProducer = {
    this.verbose = Some(verbose)
    this
  }

  def buildMqttClient:Option[MqttClient] = {

    val brokerUrl = TBOptions.getBrokerUrl
    val clientId  = TBOptions.getClientId

    val persistence = TBOptions.getPersistence
    val client = new MqttClient(brokerUrl, clientId, persistence)

    Some(client)

  }

  def isConnected:Boolean = connected

  /**
   * The `deviceToken` is either a registered ThingsBoard
   * `device` or a `gateway` token.
   */
  def start(deviceToken:String):Unit = {

    if (mqttClient.isEmpty) buildMqttClient

    val mqttOptions = TBOptions.getMqttOptions(deviceToken)
    mqttClient.get.connect(mqttOptions)

    connected = mqttClient.get.isConnected

    if (verbose.get) {
      val now = new java.util.Date()
      println(s"[INFO] $now.toString - TB Producer is connected to ThingsBoard.")
    }

  }

  def stop():Unit = {

    if (mqttClient.isEmpty) return
    mqttClient.get.disconnect()

    if (verbose.get) {
      val now = new java.util.Date()
      println(s"[INFO] $now.toString - TB Producer is disconnected from ThingsBoard.")
    }

  }

  /**
   * This is the basic method to send a certain
   * MQTT message in a ThingsBoard compliant
   * format to the server
   */
  def publish(mqttPayload:String):Unit = {

    if (mqttClient.isEmpty || !connected) {

      val now = new java.util.Date()
      throw new Exception(s"[ERROR] $now.toString - TB Producer is not connected to ThingsBoard.")

    }

    if (mqttTopic.isEmpty) {

      val now = new java.util.Date()
      throw new Exception(s"[ERROR] $now.toString - No Mqtt topic configured for TB Producer.")

    }
    try {

      val mqttMessage = new MqttMessage(mqttPayload.getBytes("UTF-8"))
      mqttMessage.setQos(TBOptions.getQos)

      mqttClient.get.publish(mqttTopic.get, mqttMessage)

    } catch {
      case t:MqttException =>

        val reasonCode = t.getReasonCode
        val cause = t.getCause

        val message = t.getMessage
        val now = new java.util.Date()

        throw new Exception(s"[ERROR] $now.toString - Mqtt publishing failed: Reason=$reasonCode, Cause=$cause, Message=$message")

    }


  }

}
