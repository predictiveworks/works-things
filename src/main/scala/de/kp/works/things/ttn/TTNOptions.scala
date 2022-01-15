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

import de.kp.works.things.ThingsConf
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

object TTNOptions {

  /**
   * The internal configuration is used, if the current
   * configuration is not set here
   */
  if (!ThingsConf.isInit) ThingsConf.init()

  private val thingsCfg = ThingsConf.getTTNCfg

  def getBrokerUrl:String = thingsCfg.getString("mqttUrl")

  def getClientId:String = thingsCfg.getString("clientId")

  def getMqttOptions:MqttConnectOptions = {
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
    /*
     * Authentication
     */
    val mqttUser = thingsCfg.getString("mqttUser")
    options.setUserName(mqttUser)

    val mqttPass = thingsCfg.getString("mqttPass")
    options.setPassword(mqttPass.toCharArray)
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

}
