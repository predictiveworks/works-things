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
import de.kp.works.things.mock.TsHistorical
import de.kp.works.things.tb.TBAdmin

class ProdDetail extends BaseActor {

  /**
   * __fault__resilient
   */
  override def execute(request: HttpRequest): String = {

    val json = getBodyAsJson(request)
    if (json == null) {

      warn(Messages.invalidJson())
      return buildEmptyDetail

    }

    val req = mapper.readValue(json.toString, classOf[ProdDetailReq])
    if (req.secret.isEmpty || req.secret != secret) {

      warn(Messages.unauthorizedReq())
      return buildEmptyDetail

    }

    try {

      val tbDeviceId = req.id
      /*
       * The current implementation temporarily distinguishes
       * between rooms (assets) that have devices assigned,
       * and those have not yet.
       *
       * Those rooms that are simulated are identified via
       * their suffix
       */
      val output = if (!tbDeviceId.endsWith(".MOCK")) {
        /*
         * This request refers to an existing device and the
         * timeseries is retrieved by requesting ThingsBoard.
         *
         * The result is the timeseries of the last month
         */
        val tbAdmin = new TBAdmin()
        if (!tbAdmin.login())
          throw new Exception("Login to ThingsBoard failed.")

        val tbKeys = Seq(req.sensor)
        /*
         * The timeseries contains all sensors (keys)
         * that are assigned to the specific devices
         */
        val tbValues =
          getDeviceTs(tbAdmin, tbDeviceId, tbKeys, req.sensor, req.interval)
          /*
           * Transform time series into UI format
           */
          .map(tbPoint => {
            Map("date" -> tbPoint.ts, req.sensor -> tbPoint.value)
          })

        /*
         * Requests to ThingsBoard are finished,
         * so logout and prepare for next login
         */
        tbAdmin.logout()
        tbValues

      } else {
        /*
         * This is a request to a room that temporarily
         * does not have any sensors assigned
         */
        val mockValues = TsHistorical.getData(req.sensor)
        mockValues
      }

      mapper.writeValueAsString(output)

    } catch {
      case t:Throwable =>
        error(Messages.failedDetailReq(t))
        buildEmptyDetail
    }

  }
}
