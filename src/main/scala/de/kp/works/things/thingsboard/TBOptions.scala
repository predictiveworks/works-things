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

import de.kp.works.things.ThingsConf
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

object TBOptions {

  private val TOPIC = "v1/devices/me/telemetry"

  /**
   * The internal configuration is used, if the current
   * configuration is not set here
   */
  if (!ThingsConf.isInit) ThingsConf.init()

  private val thingsCfg = ThingsConf.getThingsCfg

  def getBrokerUrl:String = thingsCfg.getString("mqttUrl")

  def getClientId:String = thingsCfg.getString("clientId")

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
     * the device token, that is used as password
     */
    options.setPassword(deviceToken.toCharArray)
    /*
     * Connect with MQTT 3.1 or MQTT 3.1.1
     *
     * Depending which MQTT broker you are using, you may want to explicitely
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

  def getMqttTopic:String = TOPIC

}
/*
 public static void main(String[] args) {

           String topic        = "MQTT Examples";
           String content      = "Message from MqttPublishSample";
           int qos             = 2;
           String broker       = "tcp://mqtt.eclipseprojects.io:1883";
           String clientId     = "JavaSample";
           MemoryPersistence persistence = new MemoryPersistence();

           try {
               MqttClient sampleClient = new MqttClient(broker, clientId, persistence);
               MqttConnectOptions connOpts = new MqttConnectOptions();
               connOpts.setCleanSession(true);
               System.out.println("Connecting to broker: "+broker);
               sampleClient.connect(connOpts);
               System.out.println("Connected");
               System.out.println("Publishing message: "+content);
               MqttMessage message = new MqttMessage(content.getBytes());
               message.setQos(qos);
               sampleClient.publish(topic, message);
               System.out.println("Message published");
               sampleClient.disconnect();
               System.out.println("Disconnected");
               System.exit(0);
           } catch(MqttException me) {
               System.out.println("reason "+me.getReasonCode());
               System.out.println("msg "+me.getMessage());
               System.out.println("loc "+me.getLocalizedMessage());
               System.out.println("cause "+me.getCause());
               System.out.println("excep "+me);
               me.printStackTrace();
           }
       }
   }
  */
