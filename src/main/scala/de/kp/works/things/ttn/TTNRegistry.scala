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

import com.google.gson.JsonObject
import de.kp.works.things.ttn.transformer.TTNFactory

/**
 * The [Registry] is responsible for transforming
 * device specific TTN payloads into Things specific
 * payloads.
 *
 * This transformer is used before payloads are sent
 * to the ThingsBoard producer [TBProducer].
 *
 * https://www.thethingsindustries.com/docs/reference/data-formats/
 */
object TTNRegistry {

  /**
   * FIELD NAMES
   */
  private val TTN_DECODED_PAYLOAD = "decoded_payload"
  private val TTN_DEVICE_ID       = "device_id"
  private val TTN_END_DEVICE_IDS  = "end_device_ids"
  private val TTN_RX_METADATA     = "rx_metadata"
  private val TTN_UPLINK_MESSAGE  = "uplink_message"

  def transform(messageObj:JsonObject):Option[JsonObject] = {
    /*
     * STEP #1: Extract the unique TTN device identifier;
     * this identifier is used to determine the correct
     * decoding
     *
     * {
     *  "end_device_ids" : {
     *    "device_id" : "dev1",                    // Device ID
     *    "application_ids" : {
     *      "application_id" : "app1"              // Application ID
     *    },
     *    "dev_eui" : "0004A30B001C0530",          // DevEUI of the end device
     *    "join_eui" : "800000000000000C",         // JoinEUI of the end device (also known as AppEUI in LoRaWAN versions below 1.1)
     *    "dev_addr" : "00BCB929"                  // Device address known by the Network Server
     * },
     *
     * ...
     */
    val endDeviceIds = messageObj.get(TTN_END_DEVICE_IDS).getAsJsonObject
    val ttnDeviceId = endDeviceIds.get(TTN_DEVICE_ID).getAsString
    /*
     * STEP #2: Transform decoded payload
     */
    val uplinkMessage = messageObj.get(TTN_UPLINK_MESSAGE).getAsJsonObject
    val decodedPayload = uplinkMessage.get(TTN_DECODED_PAYLOAD).getAsJsonObject
    /*
     * Retrieve manufacturer by provided unique
     * TTN device identifier; if registered, get
     * the respective transform implementation and
     * build ThingsBoard specific payload
     */
    val ttnManufacturer = TTNFactory.getTTNTransform(ttnDeviceId)
    if (ttnManufacturer.isEmpty) None
    else
      ttnManufacturer.get.transform(ttnDeviceId, decodedPayload)

  }

  // TODO
  def getGatewayIds(messageObj:JsonObject):List[String] = {

    List.empty[String]
  }
  /*
      "rx_metadata": [
        {
          "gateway_ids": {
            "gateway_id": "hutundstiel-lg308-1333720",
            "eui": "A8404121250C4150"
          },
          "time": "2022-02-10T08:17:58.335328Z",
          "timestamp": 2391186835,
          "rssi": -41,
          "channel_rssi": -41,
          "snr": 7.8,
          "uplink_token": "CicKJQoZaHV0dW5kc3RpZWwtbGczMDgtMTMzMzcyMBIIqEBBISUMQVAQk7ua9AgaDAi2lJOQBhC5j6bCASC4jN7uy0UqDAi2lJOQBhCA5vKfAQ==",
          "channel_index": 2
        }
      ],

   */
}
