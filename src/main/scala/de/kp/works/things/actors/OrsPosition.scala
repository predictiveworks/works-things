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
import com.google.gson.{JsonArray, JsonObject, JsonParser}
import de.kp.works.things.mock.GeoPoints

class OrsPosition extends BaseActor {

  /**
   * __fault__resilient
   */
  override def execute(request: HttpRequest): String = {

    val json = getBodyAsJson(request)
    if (json == null) {

      warn(Messages.invalidJson())
      return buildEmptyPosition

    }

    val req = mapper.readValue(json.toString, classOf[OrsPositionReq])
    if (req.secret.isEmpty || req.secret != secret) {

      warn(Messages.unauthorizedReq())
      return buildEmptyPosition

    }

    try {
      /*
       * The mock approach leverages a list of coordinates
       * that have be pre-computed
       */
      val mockCoords = GeoPoints.getCoordinates
      if (req.step < mockCoords.length) {

        val position = mockCoords(req.step)
        val output = Map(
          "ts" -> System.currentTimeMillis, "step" -> req.step, "latlon" -> position
        )

        val result = mapper.writeValueAsString(output)
        result

      } else buildEmptyPosition

    } catch {
      case t:Throwable =>
        error(Messages.failedPositionReq(t))
        buildEmptyPosition
    }

  }

}

