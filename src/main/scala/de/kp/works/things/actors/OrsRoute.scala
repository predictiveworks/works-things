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
import de.kp.works.things.map.OrsPoint
import de.kp.works.things.mock.GeoPoints

class OrsRoute extends BaseActor {

  /**
   * __fault__resilient
   */
  override def execute(request: HttpRequest): String = {

    val json = getBodyAsJson(request)
    println(json)
    if (json == null) {

      warn(Messages.invalidJson())
      return buildEmptyRoute

    }

    val req = mapper.readValue(json.toString, classOf[OrsRouteReq])
    if (req.secret.isEmpty || req.secret != secret) {

      warn(Messages.unauthorizedReq())
      return buildEmptyRoute

    }

    try {

      val mockCoords = GeoPoints.getCoordinates

      var startIndex:Int = -1
      var endIndex:Int   = -1

      mockCoords.indices.foreach(index => {
        val coord = mockCoords(index)

        if (coord.lat == req.startLat && coord.lon == req.startLon)
          startIndex = index

        if (coord.lat == req.endLat && coord.lon == req.endLon)
          endIndex = index

      })
      println(startIndex)
      println(endIndex)
      var points = List.empty[OrsPoint]

      if ((startIndex + 1 < mockCoords.length) && (endIndex + 1 < mockCoords.length))
        points = mockCoords.slice(startIndex + 1, endIndex + 1)

      println(points)
      val output = mapper.writeValueAsString(points)
      output

    } catch {
      case t:Throwable =>
        t.printStackTrace()
        error(Messages.failedDetailReq(t))
        buildEmptyRoute
    }

  }

}
