package de.kp.works.things.owea

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

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.google.gson.{JsonElement, JsonObject}
import de.kp.works.things.http.HttpConnect
import de.kp.works.things.json.JsonUtil
import de.kp.works.things.logging.Logging
import de.kp.works.things.tb._

import java.util.concurrent.{ExecutorService, Executors, ScheduledExecutorService, TimeUnit}
import scala.concurrent.ExecutionContextExecutor

class OweaMonitor(numThreads:Int = 1) extends Logging {

  private val uuid = java.util.UUID.randomUUID.toString
  /**
   * Akka 2.6 provides a default materializer out of the box, i.e., for Scala
   * an implicit materializer is provided if there is an implicit ActorSystem
   * available. This avoids leaking materializers and simplifies most stream
   * use cases somewhat.
   */
  implicit val system: ActorSystem = ActorSystem(s"owea-monitor-$uuid")
  implicit lazy val context: ExecutionContextExecutor = system.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  /**
   * The configured time interval the consumer task
   * is executed
   */
  private val interval =OweaOptions.getTimeInterval

  private var executorService:ScheduledExecutorService = _
  private val consumer = new OweaConsumer(system)

  def start():Unit = {

    val worker = new Runnable {

      override def run(): Unit = {
        info(s"Owea Consumer started.")
        consumer.extractStations()
      }
    }

    try {

      executorService = Executors.newScheduledThreadPool(numThreads)
      executorService.scheduleAtFixedRate(worker, 0, interval, TimeUnit.MILLISECONDS)


    } catch {
      case t:Exception =>
        error(s"Owea Monitor failed with: ${t.getLocalizedMessage}")
        stop()
    }

  }

  def stop():Unit = {

    executorService.shutdown()
    executorService.shutdownNow()

    system.terminate()

  }

}

/**
 * This class consumes weather data for a configured
 * list of geo spatial locations (weather stations)
 * in recurring (configured) time intervals and sends
 * the retrieved data to a ThingsBoard server instance
 * via MQTT.
 *
 * The current implementation treats each weather station
 * as an independent devices and therefore leverages the
 * ThingsBoard Device API to publish weather data.
 */
class OweaConsumer(oweaSystem:ActorSystem) extends HttpConnect with JsonUtil with Logging {

  private val apiUrl = OweaOptions.getBaseUrl
  private val apiKey = OweaOptions.getApiKey

  private val stations = OweaOptions.getStations
  private val sleep = 1000

  def extractStations():Unit = {

    println("Airq Consumer: extractStations")

    /*
     * Move through all configured weather stations and
     * retrieve the corresponding weather data
     */
    stations.foreach(station => {
      /*
       * Retrieve the weather data for the geo spatial
       * coordinates of the provided location
       */
      val lat = station.lat
      val lon = station.lon

      val weather = getByLatLon(lat, lon).getAsJsonObject
      val deviceSpecs = List(
        Map(
          "prefix" -> "DEV.CLOU",
          "attrs"  -> Seq("cloudiness")
        ),
        Map(
          "prefix" -> "DEV.HUMD",
          "attrs"  -> Seq("humidity")
        ),
        Map(
          "prefix" -> "DEV.PRESS",
          "attrs"  -> Seq("pressure")
        ),
        Map(
          "prefix" -> "DEV.TEMP",
          "attrs"  -> Seq("temp", "feels_like", "temp_min", "temp_max")
        ),
        Map(
          "prefix" -> "DEV.VISB",
          "attrs"  -> Seq("visibility")
        ),
        Map(
          "prefix" -> "DEV.WIND",
          "attrs"  -> Seq("wind_speed", "wind_deg", "wind_gust")
        )

      )

      deviceSpecs.foreach(deviceSpec =>
        extractStation(station, deviceSpec, weather))

      Thread.sleep(sleep)

    })

  }

  private def extractStation(station:OweaStation, deviceSpec:Map[String, Any], weather:JsonObject):Unit = {
    /*
     * Transform wind data into TBRecord
     */
    val attrNames = deviceSpec("attrs").asInstanceOf[Seq[String]]
    val tbColumns = attrNames.map(attrName => {

      val attrVal = getDoubleWithScale(weather, attrName)
      TBColumn(attrName, attrVal)

    })

    val timestamp = weather.get("timestamp").getAsLong
    val tbRecord = TBRecord(timestamp, tbColumns)

    val tbTimeseries = TBTimeseries(Seq(tbRecord))

    val prefix = deviceSpec("prefix").asInstanceOf[String]
    val tbDeviceName = s"$prefix.${station.id.replace("STA", "")}}"
    /*
     * Build device specific actor and send TBJob
     * to this actor to publish time series records.
     *
     * This actor is automatically destroyed after
     * the provided TBJob is executed
     */
    val tbDeviceActor = oweaSystem.actorOf(
      Props(new TBProducer()), s"$tbDeviceName-actor")

    val tbJob = TBJob(tbDeviceName, tbTimeseries)
    tbDeviceActor ! tbJob

  }

  private def getByCityName(cityName:String):JsonElement = {

    val endpoint = s"${apiUrl}q={$cityName}&appid=$apiKey"

    val bytes = get(endpoint)
    val response = extractJsonBody(bytes)

    val weather = extractWeather(response.getAsJsonObject)
    weather

  }

  private def getByLatLon(lat:Double, lon:Double):JsonElement = {

    val endpoint = s"${apiUrl}lat=$lat&lon=$lon&appid=$apiKey"

    val bytes = get(endpoint)
    val response = extractJsonBody(bytes)

    val weather = extractWeather(response.getAsJsonObject)
    weather

  }

  private def extractWeather(jsonObject:JsonObject):JsonElement = {
    /*
     * Flatten [[OpenWeatherAPI]] response
     */
    val weather = initialObject
    /*
     * STEP #1: Extract and append geographical
     * coordinates; note, the subordinate digits
     * are restricted to 4
     *
     * 	"coord": {
     * 		"lon": -122.08,
     * 		"lat": 37.39
     * 	},
     */
    val jCoord = jsonObject.get("coord").getAsJsonObject

    val lat = jCoord.get("lat").getAsDouble
    val lon = jCoord.get("lon").getAsDouble

    weather.addProperty("lat", lat)
    weather.addProperty("lon", lon)
    /*
     * STEP #2: Extract and append `weather`
     *
     * 	"weather": [
     *  		{
     *      "id": 800,
     *      "main": "Clear", [Group of weather parameters (Rain, Snow, Extreme etc.)]
     *      "description": "clear sky",
     *      "icon": "01d"
     *     }
     * 	]
     *
     */
    val jWeathers = jsonObject.get("weather").getAsJsonArray
    if (jWeathers.size > 0) {

      val jWeather = jWeathers.get(0).getAsJsonObject

      weather.addProperty("desc", jWeather.get("description").getAsString)
      weather.addProperty("icon", jWeather.get("icon").getAsString)
      weather.addProperty("main", jWeather.get("main").getAsString)

    }
    /*
     * STEP #3: Extract and append `main`
     *
     *		"main": {
     *
     * 		Default unit: Kelvin
     *   	"temp": 293.06,
     *  		"feels_like": 292.23,
     *  		"temp_min": 291.98,
     *  		"temp_max": 294.25,
     *
     *  		Default hPa on the sea level if there is no `sea_level`
     *  		or `grnd_level` data
     *  		"pressure": 1014,
     *
     *  		[optional]
     *  		sea_level: [hPa],
     *  		grnd_level: [hPa]
     *
     *  		"humidity": 43 [percentage]
     *		}
     *
     */
    val jMain = getAsObject(jsonObject, "main")
    if (jMain != null) {

      weather.addProperty("temp",       getOrElseDouble(jMain, "temp", 0D) - 273.15)
      weather.addProperty("feels_like", getOrElseDouble(jMain, "feels_like", 0D) -273.15)
      weather.addProperty("temp_min",   getOrElseDouble(jMain, "temp_min", 0D) -273.15)
      weather.addProperty("temp_max",   getOrElseDouble(jMain, "temp_max", 0D) -273.15)

      weather.addProperty("pressure",   getOrElseDouble(jMain, "pressure", -1D))
      weather.addProperty("humidity",   getOrElseDouble(jMain, "humidity", -1D))

    }
    /*
     * 	"visibility": 10000 [meter]
     */
    weather.addProperty("visibility", getOrElseDouble(jsonObject, "visibility", -1))
    /*
     *
     * 	"wind": {
     *  	"speed": 4.02, [meter/sec]
     *   	"deg": 138,		[Degrees, meteorological]
     *   	"gust": 6.71   [meter/sec]
     *	}
     *
     */
    val jWind = getAsObject(jsonObject, "wind")
    if (jWind != null) {

      weather.addProperty("wind_speed", getOrElseDouble(jWind, "speed", -1D))
      weather.addProperty("wind_deg",   getOrElseDouble(jWind, "deg",  -1D))
      weather.addProperty("wind_gust",  getOrElseDouble(jWind, "gust",  -1D))

    }
    /*
     *		"clouds": {
     *   	  "all": 0 [Cloudiness, %]
     *		}
     *
     */
    val jclouds = getAsObject(jsonObject, "clouds")
    if (jclouds != null) {
      weather.addProperty("cloudiness", getOrElseDouble(jclouds, "all", -1D))
    }
    /*
     *  "dt": 1622046459 [Unix UTC]
     */
    val timestamp = jsonObject.get("dt").getAsInt.toLong * 1000
    weather.addProperty("timestamp", timestamp.toLong)
    /*
     *		"sys": {
     *  		"type": 2,
     *  		"id": 2002879,
     *  		"country": "AT",
     *  		"sunrise": 1621998169,
     *  		"sunset": 1622054397
     *		}
     *
     */
    val jSys = jsonObject.get("sys").getAsJsonObject

    val country = jSys.get("country").getAsString
    weather.addProperty("country", country)

    val sunrise = jSys.get("sunrise").getAsInt.toLong * 1000 // UTC
    weather.addProperty("sunrise", sunrise)

    val sunset = jSys.get("sunset").getAsInt.toLong * 1000  // UTC
    weather.addProperty("sunset", sunset)

    /*
     * 	"timezone": 7200 - Shift from UTC in seconds 2h = 2 * 3600 = 7200
     *  "name": "Aspern" (Weather station)
     */
    val timezone = jsonObject.get("timezone").getAsInt.toLong * 1000
    weather.addProperty("timezone", timezone)

    val name = jsonObject.get("name").getAsString
    weather.addProperty("station", name)
    /*
     * 	"rain": {
     * 		"1h": ... [mm]
     * 		"3h": ... [mm]
     *  }
     */
    val jRain = getAsObject(jsonObject, "rain")
    if (jRain != null) {
      weather.addProperty("rain_1h", getOrElseInt(jRain, "1h", -1))
      weather.addProperty("rain_3h", getOrElseInt(jRain, "3h", -1))
    }

    /*
     * 	"snow": {
     * 		"1h": ... [mm]
     * 		"3h": ... [mm]
     *  }
     */
    val jSnow = getAsObject(jsonObject, "snow")
    if (jSnow != null) {
      weather.addProperty("snow_1h", getOrElseInt(jSnow, "1h", -1))
      weather.addProperty("snow_3h", getOrElseInt(jSnow, "3h", -1))
    }

    weather

  }

  private def initialObject:JsonObject = {

    val weather = new JsonObject()

    weather.addProperty("lat", 0D)
    weather.addProperty("lon", 0D)

    weather.addProperty("desc", "")
    weather.addProperty("icon", "")
    weather.addProperty("main", "")

    /* The default temperature: 0 K = -273.15 °C */
    weather.addProperty("temp",       -273.15)
    weather.addProperty("feels_like", -273.15)
    weather.addProperty("temp_min",   -273.15)
    weather.addProperty("temp_max",   -273.15)

    weather.addProperty("pressure", -1D)
    weather.addProperty("humidity", -1D)

    weather.addProperty("wind_speed", -1D)
    weather.addProperty("wind_deg",   -1D)
    weather.addProperty("wind_gust",  -1D)

    weather.addProperty("cloudiness", -1D)

    weather.addProperty("rain_1h", -1)
    weather.addProperty("rain_3h", -1)

    weather.addProperty("snow_1h", -1)
    weather.addProperty("snow_3h", -1)

    weather

  }

}
