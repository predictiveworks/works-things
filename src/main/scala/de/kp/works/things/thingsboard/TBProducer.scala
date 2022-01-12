package de.kp.works.things.thingsboard

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

import org.eclipse.paho.client.mqttv3.{MqttClient, MqttException, MqttMessage}

/**
 * The [TBProducer] is responsible for writing MQTT
 * message that refer to a certain device to the
 * ThingsBoard server
 */
class TBProducer {

  private val mqttClient: Option[MqttClient] = buildMqttClient
  private var connected:Boolean = false

  def buildMqttClient:Option[MqttClient] = {

    val brokerUrl = TBOptions.getBrokerUrl
    val clientId  = TBOptions.getClientId

    val persistence = TBOptions.getPersistence
    val client = new MqttClient(brokerUrl, clientId, persistence)

    Some(client)

  }

  def start(deviceToken:String):Unit = {

    if (mqttClient.isEmpty) buildMqttClient

    val mqttOptions = TBOptions.getMqttOptions(deviceToken)
    mqttClient.get.connect(mqttOptions)

    connected = mqttClient.get.isConnected

    val now = new java.util.Date()
    println(s"[INFO] $now.toString - Mqtt Producer is connected to ThingsBoard.")

  }

  def stop():Unit = {

    if (mqttClient.isEmpty) return
    mqttClient.get.disconnect()

    val now = new java.util.Date()
    println(s"[INFO] $now.toString - Mqtt Producer is disconnected from ThingsBoard.")

  }

  def publish(content:String):Unit = {

    if (mqttClient.isEmpty || !connected) {

      val now = new java.util.Date()
      throw new Exception(s"[INFO] $now.toString - Mqtt Producer is not connected to ThingsBoard.")

    }

    try {

      val mqttMessage = new MqttMessage(content.getBytes("UTF-8"))
      mqttMessage.setQos(TBOptions.getQos)

      val mqttTopic = TBOptions.getMqttTopic
      mqttClient.get.publish(mqttTopic, mqttMessage)

    } catch {
      case t:MqttException =>

        val reasonCode = t.getReasonCode
        val cause = t.getCause

        val message = t.getMessage
        val now = new java.util.Date()

        throw new Exception(s"[INFO] $now.toString - Mqtt publishing failed: Reason=$reasonCode, Cause=$cause, Message=$message")

    }


  }

}
