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
import de.kp.works.things.prod.ProdOptions
import de.kp.works.things.tb.TBAdmin

class ProdStation extends BaseActor {

  /**
   * Every production station is organized in different
   * rooms; currently 2 rooms, incubation and fruition
   * are supported
   */
  private val rooms = ProdOptions.getRooms

  /**
   * __fault__resilient
   */
  override def execute(request: HttpRequest): String = {

    val json = getBodyAsJson(request)
    if (json == null) {

      warn(Messages.invalidJson())
      return buildEmptyStation

    }

    val req = mapper.readValue(json.toString, classOf[ProdStationReq])
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

      /*
       * Retrieve room identifier from the provided
       * request `room` name; this identifier must
       * be used in a subsequent `detail` request.
       */
      val tbAssetName = rooms
        .filter(room => room.`type` == req.room && room.station == req.id)
        .head.id

      /*
       * Retrieve attribute mapping to enable proper
       * visualization of TTN device attributes
       */
      val mappings = ProdOptions
        .getMappingsByAsset(tbAssetName)

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
       *  mappings: {
       *    id: "...",
       *    labels: ["...", "...", ]
       *    mappings: [
       *      {
       *        label": "...",
       *        sensor": "...",
       *        units: "..."
       *      }
       *    ]
       *  },
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
      val mockValues = List("co2", "temp", "humd", "no2").map(sensor => {
        Map(
          "device" -> s"DEV.${sensor.toUpperCase()}",
          "values" -> List(
            Map("name" -> sensor, "value" -> new java.util.Random().nextInt(100))
          )
        )

      })

      val output = Map("ts" -> timestamp, "mappings" -> mappings, "values" -> mockValues)
      mapper.writeValueAsString(output)

    } catch {
      case t:Throwable =>
        error(Messages.failedStationReq(t))
        buildEmptyStation
    }

  }
}
