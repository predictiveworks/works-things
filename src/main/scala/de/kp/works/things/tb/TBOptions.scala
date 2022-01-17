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

import com.typesafe.config.Config
import de.kp.works.things.ThingsConf
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

object TBOptions {
  /**
   * This is the device telemetry topic defined to send messages
   * to the ThingsBoard server:
   *
   * The simplest format
   *
   * {"key1":"value1", "key2":"value2"}
   *
   *  In this case, the server-side timestamp will be assigned
   *  to uploaded data. Providing own timestamps requires the
   *  following format
   *
   *  {"ts":1451649600512, "values":{"key1":"value1", "key2":"value2"}}
   *
   * The timestamp is a unix timestamp with milliseconds precision
   */
  val DEVICE_TELEMETRY_TOPIC = "v1/devices/me/telemetry"
  /**
   * This is the gateway telemetry topic defined to send messages
   * to the ThingsBoard server
   *
   * {
   * "Device A": [
   *  {
   *     "ts": 1483228800000,
   *     "values": {
   *        "temperature": 42,
   *        "humidity": 80
   *     }
   *  },
   *  {
   *      "ts": 1483228801000,
   *      "values": {
   *         "temperature": 43,
   *         "humidity": 82
   *      }
   *  }
   * ],
   * "Device B": [
   *  {
   *      "ts": 1483228800000,
   *      "values": {
   *          "temperature": 42,
   *          "humidity": 80
   *      }
   *  }
   * ]
   * }
   */
  val GATEWAY_TELEMETRY_TOPIC = "v1/gateway/telemetry"
  /**
   * This is the gateway topic to connect a certain device to
   * the gateway
   *
   * Format {"device":"Device A"} where Device A is the device
   * name. Once received, ThingsBoard will lookup or create a
   * device with the name specified.
   *
   * Also, ThingsBoard will publish messages about new attribute
   * updates and RPC commands for a particular device to this Gateway.
   *
   */
  val GATEWAY_CONNECT_TOPIC = "v1/gateway/connect"
  /**
   * This is the gateway topic to disconnect a certain device
   * from the gateway
   *
   * Format {"device":"Device A"} where Device A is the device
   * name. Once received, ThingsBoard will no longer publish
   * updates for this particular device to this Gateway.
   */
  val GATEWAY_DISCONNECT_TOPIC = "v1/gateway/disconnect"
  /**
   * This is the gateway topic to publish device attributes to
   * the ThingsBoard server
   *
   * Format
   *
   * {
   *  "Device A":
   *    {
   *      "attribute1":"value1",
   *      "attribute2": 42
   *    },
   *  "Device B":
   *    {
   *      "attribute1":"value1",
   *      "attribute2": 42
   *    }
   *  }
   */
  val GATEWAY_ATTRIBUTES_TOPIC = "v1/gateway/attributes"

  /**
   * The internal configuration is used, if the current
   * configuration is not set here
   */
  if (!ThingsConf.isInit) ThingsConf.init()

  private val thingsCfg = ThingsConf.getTBCfg

  def getAdminCfg:Config = thingsCfg.getConfig("admin")

  def getBrokerUrl:String = thingsCfg.getString("mqttUrl")

  def getClientId:String = thingsCfg.getString("clientId")

  def getMobileCfg:Config = thingsCfg.getConfig("mobile")

  def getMqttOptions(deviceToken:String):MqttConnectOptions = {
    /*
     * The MQTT connection is configured to
     * enable automatic re-connection
     */
    val options = new MqttConnectOptions()
    options.setAutomaticReconnect(true)

    options.setCleanSession(true)

    val timeout = thingsCfg.getInt("timeout")
    options.setConnectionTimeout(timeout)

    val keepAlive = thingsCfg.getInt("keepAlive")
    options.setKeepAliveInterval(keepAlive)

    /* Authentication
     *
     * Access to a certain ThingsBoard device requires
     * the device token, that is used as user name
     */
    options.setUserName(deviceToken)
    /*
     * Connect with MQTT 3.1 or MQTT 3.1.1
     *
     * Depending which MQTT broker you are using, you may want to explicitly
     * connect with a specific MQTT version.
     *
     * By default, Paho tries to connect with MQTT 3.1.1 and falls back to
     * MQTT 3.1 if it’s not possible to connect with 3.1.1.
     *
     * We therefore do not specify a certain MQTT version.
     */
    val mqttVersion = thingsCfg.getInt("mqttVersion")
    options.setMqttVersion(mqttVersion)

    options

  }

  /** 						MESSAGE PERSISTENCE
   *
   * Since we don’t want to persist the state of pending
   * QoS messages and the persistent session, we are just
   * using a in-memory persistence. A file-based persistence
   * is used by default.
   */
  def getPersistence:MemoryPersistence = new MemoryPersistence()

  def getQos:Int = thingsCfg.getInt("mqttQoS")

  def getMqttTopic:String = DEVICE_TELEMETRY_TOPIC

}
