package de.kp.works.things.ttn

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

import com.google.gson.JsonParser
import de.kp.works.things.tb.TBProducer
import org.eclipse.paho.client.mqttv3.{IMqttDeliveryToken, MqttCallback, MqttClient, MqttMessage}

/**
 * The [TTNConsumer] subscribes to a certain topic
 * of TheThingsNetwork MQTT broker and sends the
 * transformed result to the ThingsBoard server
 */
class TTNConsumer {

  private val mqttClient: Option[MqttClient] = buildMqttClient

  private var mqttTopic:Option[String] = None
  private var tbProducer:Option[TBProducer] = None

  /**
   * This method assigns the ThingsBoard message
   * producer to this MQTT consumer
   */
  def setTBProducer(tbProducer:TBProducer):TTNConsumer = {
    this.tbProducer = Some(tbProducer)
    this
  }

  def setTopic(topic:String):TTNConsumer = {
    this.mqttTopic = Some(topic)
    this
  }

  def buildMqttClient:Option[MqttClient] = {

    val brokerUrl = TTNOptions.getBrokerUrl
    val clientId  = TTNOptions.getClientId

    val persistence = TTNOptions.getPersistence
    val client = new MqttClient(brokerUrl, clientId, persistence)

    Some(client)

  }

  def start():Unit = {
    /*
     * Callback automatically triggers as and when new message
     * arrives on specified topic
     */
    val callback: MqttCallback = new MqttCallback() {

      override def messageArrived(topic: String, message: MqttMessage) {

        val payload = message.getPayload
        transform(message)
          //tbProducer.get.publish(transform(message))

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

    if (mqttTopic.isEmpty) {

      val now = new java.util.Date()
      throw new Exception(
        s"[ERROR] $now.toString - No Mqtt topic configured for TTN Consumer.")

    }
    /*
     * Set up callback for MqttClient. This needs to happen before
     * connecting or subscribing, otherwise messages may be lost
     */
    mqttClient.get.setCallback(callback)

    val mqttOptions = TTNOptions.getMqttOptions
    mqttClient.get.connect(mqttOptions)

    if (!mqttClient.get.isConnected) {

      val now = new java.util.Date()
      throw new Exception(
        s"[ERROR] $now.toString - TTN Consumer could not connect to TheThingsNetwork.")

    }

    val now = new java.util.Date()
    println(s"[INFO] $now.toString - TTN Consumer is connected to TheThingsNetwork.")

    mqttClient.get.subscribe(mqttTopic.get, TTNOptions.getQos)

  }

  def restart(t:Throwable): Unit = {

    val now = new java.util.Date()
    println(s"[WARN] $now - TTN Consumer restart due to: ${t.getLocalizedMessage}")

    start()

  }

  def stop():Unit = {

    if (mqttClient.isEmpty) return
    mqttClient.get.disconnect()

    val now = new java.util.Date()
    println(s"[INFO] $now.toString - TTN Consumer is disconnected from TheThingsNetwork.")

  }

  def transform(mqttMessage:MqttMessage):String = {

    val payload = mqttMessage.getPayload
    val json = JsonParser.parseString(new String(payload))
    /*
    {
      "end_device_ids":{
        "device_id":"eui-70b3d57ed004b31f",
        "application_ids":{
          "application_id":"hutundstiel"
        },
        "dev_eui":"70B3D57ED004B31F",
        "join_eui":"0000000000000000"
      },
      "correlation_ids":[
        "as:up:01FSA2DJPQRY23E48XFHXAW2EC",
        "rpc:/ttn.lorawan.v3.AppAs/SimulateUplink:0cc36914-91fd-4cc5-ab3b-f46b0881cc90"
      ],
      "received_at":"2022-01-13T15:55:35.513107768Z",
      "uplink_message":{
        "f_port":2,
        "frm_payload":"y6QKuwJcAX//f/8=",
        "decoded_payload":{
          "BatV":2.98,
          "Bat_status":3,
          "Ext_sensor":"Temperature Sensor",
          "Hum_SHT":60.4,
          "TempC_DS":327.67,
          "TempC_SHT":27.47
        },
        "rx_metadata":[
          {"gateway_ids":{
            "gateway_id":"test"
          },
          "rssi":42,
          "channel_rssi":42,"snr":4.2
        }
      ],
      "settings":{
        "data_rate":{
          "lora":{
            "bandwidth":125000,
            "spreading_factor":7
          }
        }
      }
    },
    "simulated":true
  }

     */
    println(json)

    json.toString
  }

}
