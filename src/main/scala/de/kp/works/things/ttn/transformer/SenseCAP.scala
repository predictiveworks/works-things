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

import com.google.gson.{JsonNull, JsonObject}

import scala.collection.JavaConversions.iterableAsScalaIterable

class SenseCAP extends TTNTransform {
  /**
   * SenseCAP devices decoded payload fields
   */
  val ERR: String               = "err"
  val MEASUREMENT_ID: String    = "measurementId"
  val MEASUREMENT_VALUE: String = "measurementValue"
  val MESSAGES: String          = "messages"
  /**
   * SenseCAP measurement identifiers
   */
  val CO2: Int = 4100

  def transform(ttnDeviceId:String, decodedPayload:JsonObject):Option[JsonObject] = {

    try {

      val model = TTNIdentity.getModelById(ttnDeviceId)
      if (model.isEmpty) return None

      model.get match {
        case TTNIdentity.SENSECAP_CO2 =>
          /*
           * SenseCAP Wireless CO2 sensor
           *
           * {
           *    "err": 0,
           *    "messages": [
           *      {
           *        "measurementId": 4100,
           *        "measurementValue": 364,
           *        "type": "report_telemetry"
           *      },
           *      ...
           *    ],
           *    "payload": "010410E08D05009802",
           *    "valid": true
           * }
           */
          val err = decodedPayload.get(ERR).getAsInt
          if (err == 0) {
            /*
             * We restrict our processing to `report_telemetry`
             * message, as their may be also other message with
             * different types be provided
             */
            val message = {

              val messages = decodedPayload.get(MESSAGES).getAsJsonArray
              val filtered = messages.filter(messageElem => {

                val messageObj = messageElem.getAsJsonObject
                val messageType = messageObj.get("type").getAsString

                if (messageType == "report_telemetry") true else false

              })

              if (filtered.nonEmpty)
                filtered.head.getAsJsonObject else JsonNull.INSTANCE

            }

            if (message.isJsonObject) {

              val messageObj = message.getAsJsonObject

              val measurementId = messageObj.get(MEASUREMENT_ID).getAsInt
              if (measurementId == CO2) {

                val measurementValue = messageObj.get(MEASUREMENT_VALUE).getAsNumber

                val output = new JsonObject
                output.addProperty(TB_CO2, measurementValue)

                Some(output)

              } else
                throw new Exception("SenseCAP message received for `$ttnDeviceId` does not describe CO2 measurements.")

            } else None

          } else
            throw new Exception("SenseCAP error message received for `$ttnDeviceId`.")
        case _ => None
      }

    } catch {
      case t:Throwable =>
        error(s"SenseCAP transformation failed: ${t.getLocalizedMessage}")
        None
    }

  }

}
