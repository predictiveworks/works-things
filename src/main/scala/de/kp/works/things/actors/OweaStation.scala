package de.kp.works.things.actors

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

import akka.http.scaladsl.model.HttpRequest
import de.kp.works.things.tb.TBAdmin

class OweaStation extends BaseActor {

  /**
   * __fault__resilient
   */
  override def execute(request: HttpRequest): String = {

    val json = getBodyAsJson(request)
    if (json == null) {

      warn(Messages.invalidJson())
      return buildEmptyStation

    }

    val req = mapper.readValue(json.toString, classOf[OweaStationReq])
    if (req.secret.isEmpty || req.secret != secret) {

      warn(Messages.unauthorizedReq())
      return buildEmptyStation

    }

    try {

      /*
       * This request retrieves the latest values of the
       * sensor data (temperature, humidity, etc) that
       * are managed by ThingsBoard
       */
      val tbAdmin = new TBAdmin()
      if (!tbAdmin.login())
        throw new Exception("Login to ThingsBoard failed.")

      val tbAssetName = req.id
      val (timestamp, latestValues) = getDeviceLatest(tbAdmin, tbAssetName)

      /*
       * Requests to ThingsBoard are finished,
       * so logout and prepare for next login
       */
      tbAdmin.logout()

      /*
       * Output format:
       *
       * {
       *  ts: ...,
       *  values: [
       *    {
       *      device: "...",
       *      values: [
       *        {
       *          name: "...",
       *          value: ...
       *        }
       *      ]
       *    },
       *  ]
       * }
       */
      val mockValues = List(
        Map(
          "device" -> "DEV.CLOU",
          "values" -> List(
            Map("name" -> "cloudiness", "value" -> 45))
        ),
        Map(
          "device" -> "DEV.HUMD",
          "values" -> List(
            Map("name" -> "humidity", "value" -> 72))
        ),
        Map(
          "device" -> "DEV.PRESS",
          "values" -> List(
            Map("name" -> "pressure", "value" -> 1013))
        ),
        Map(
          "device" -> "DEV.TEMP",
          "values" -> List(
            Map("name" -> "temp",       "value" -> 3),
            Map("name" -> "feels_like", "value" -> -1),
            Map("name" -> "temp_min",   "value" -> -2),
            Map("name" -> "temp_max",   "value" -> -7)
          )
        ),
        Map(
          "device" -> "DEV.VISB",
          "values" -> List(
            Map("name" -> "visibility", "value" -> 10000))
        ),
        Map(
          "device" -> "DEV.WIND",
          "values" -> List(
            Map("name" -> "wind_speed", "value" -> 12),
            Map("name" -> "wind_deg",   "value" -> 30),
            Map("name" -> "wind_gust",  "value" -> 24)
          )
        )
      )

      val output = Map("ts" -> timestamp, "values" -> latestValues)
      mapper.writeValueAsString(output)

    } catch {
      case t:Throwable =>
        error(Messages.failedStationReq(t))
        buildEmptyStation
    }

  }
}
