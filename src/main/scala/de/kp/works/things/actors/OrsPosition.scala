package de.kp.works.things.actors

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
        error(Messages.failedDetailReq(t))
        buildEmptyPosition
    }

  }

}

