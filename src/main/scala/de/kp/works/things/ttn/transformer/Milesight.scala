package de.kp.works.things.ttn.transformer

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

class Milesight extends TTNTransform {
  /**
   * Milesight devices decoded payload fields
   */
  val BATTERY: String     = "battery"
  val HUMIDITY: String    = "humidity"
  val TEMPERATURE: String = "temperature"

  override def transform(ttnDeviceId: String, decodedPayload: JsonObject): Option[JsonObject] = {

    try {

      val model = TTNIdentity.getModelById(ttnDeviceId)
      if (model.isEmpty) return None

      model.get match {
        case TTNIdentity.EM300_TH =>
          /*
           * Milesight environment sensor EM300-TH-868M
           *
           * Source: https://github.com/Milesight-IoT/SensorDecoders/tree/master/EM300_Series/EM300-TH
           *
           * --------------------- Payload Definition ---------------------
           *
           *                  [channel_id] [channel_type]   [channel_value]
           *
           * 01: battery      -> 0x01         0x75          [1byte ] Unit: %
           * 03: temperature  -> 0x03         0x67          [2bytes] Unit: °C (℉)
           * 04: humidity     -> 0x04         0x68          [1byte ] Unit: %RH
           *
           * ------------------------------------------------------ EM300-TH
           *
           * Sample: 01 75 5C 03 67 34 01 04 68 65
           *
           * {
           * "battery": 92,
           * "humidity": 50.5,
           * "temperature": 30.8
           * }
           */
          val battery = decodedPayload.get(BATTERY).getAsNumber

          val humidity = decodedPayload.get(HUMIDITY).getAsNumber
          val temperature = decodedPayload.get(TEMPERATURE).getAsNumber

          val output = new JsonObject
          output.addProperty(TB_BATT, battery)

          output.addProperty(TB_HUMD, humidity)
          output.addProperty(TB_TEMP, temperature)

          Some(output)

        case _ => None
      }

    } catch {
        case t: Throwable =>
          error(s"Milesight transformation failed: ${t.getLocalizedMessage}")
          None
    }

  }
}