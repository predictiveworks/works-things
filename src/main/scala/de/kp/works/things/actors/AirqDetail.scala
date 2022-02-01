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

class AirqDetail extends BaseActor {

  /**
   * __fault__resilient
   */
  override def execute(request: HttpRequest): String = {

    val json = getBodyAsJson(request)
    if (json == null) {

      warn(Messages.invalidJson())
      return buildEmptyDetail

    }

    val req = mapper.readValue(json.toString, classOf[AirqDetailReq])

    if (req.secret.isEmpty || req.secret != secret) {

      warn(Messages.unauthorizedReq())
      return buildEmptyDetail

    }

    try {
      /*
       * This request retrieves the values of the
       * last month
       */
      val tbAdmin = new TBAdmin()
      if (!tbAdmin.login())
        throw new Exception("Login to ThingsBoard failed.")

      val tbDeviceId = req.id
      val tbKeys = Seq(req.sensor)
      /*
       * The timeseries contains all sensors (keys)
       * that are assigned to the specific devices
       */
      val tbValues =
        getDeviceTs(tbAdmin, tbDeviceId, tbKeys, req.sensor)
      /*
       * Requests to ThingsBoard are finished,
       * so logout and prepare for next login
       */
      tbAdmin.logout()

      val mockValues = TsHistorical.getData(req.sensor)
      mapper.writeValueAsString(mockValues)

    } catch {
      case t:Throwable =>
        error(Messages.failedDetailReq(t))
        buildEmptyDetail
    }

  }
}
