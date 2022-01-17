package de.kp.works.things.weather

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

import com.google.gson.{JsonElement, JsonObject}
import com.typesafe.config.{Config, ConfigObject}
import de.kp.works.things.http.HttpConnect
import de.kp.works.things.json.JsonUtil
import de.kp.works.things.tb.DeviceProducer

import scala.collection.mutable

object OweaConsumer {

  private var instance:Option[OweaConsumer] = None

  def getInstance():OweaConsumer = {

    if (instance.isEmpty)
      instance = Some(new OweaConsumer)

    instance.get

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
class OweaConsumer extends HttpConnect with JsonUtil {

  private val apiUrl = OweaOptions.getBaseUrl
  private val apiKey = OweaOptions.getApiKey

  private val interval = OweaOptions.getTimeInterval
  private val stations = OweaOptions.getStations
  /*
   * Every weather station is handled as an individual device
   * and has its own device producer assigned
   */
  private val producers = mutable.HashMap.empty[String, DeviceProducer]
  /**
   * A flag that determines whether this consumer is retrieving
   * values for the [OpenWeather] API for the configured weather
   * stations and their coordinates
   */
  private var consuming = true
  /**
   * The timestamp that determine when the last weather data where
   * retrieved from the OpenWeather API
   */
  private var lastTs = 0L
  /**
   * The sleep time in milli seconds between 2 requests to the
   * [OpenWeather] API
   */
  private val sleep = 500

  def start():Unit = {

    while (consuming) {

      if (lastTs == 0L) {
        extractLocations()
      }
      else {

        if (System.currentTimeMillis - lastTs < interval) {
          extractLocations()
        }

      }
    }

  }

  def stop():Unit = {
    consuming = false
  }

  def extractLocations():Unit = {
    /*
     * Register the timestamp in milliseconds, the weather
     * data are retrieved from the [OpenWeather] API
     */
    lastTs = System.currentTimeMillis
    /*
     * Move through all configured weather stations and
     * retrieve the corresponding weather data
     */
    stations.foreach(station => {
      // TODO
      val token:String = null
      /*
       * Check whether a token (device) specific
       * producer is started
       */
      if (!producers.contains(token)) {
        try {
          val producer = new DeviceProducer
          producer.start(token)

          if (producer.isConnected)
            producers += token -> producer

          else
            throw new Exception(s"Assigning TB Producer to token `$token` failed.")

        } catch {
          case t:Throwable =>
            val now = new java.util.Date()
            println(s"[WARN] $now.toString - ${t.getLocalizedMessage}")
        }
      }

      //extractLocation(location)
      Thread.sleep(sleep)

    })

  }

  // TODO
  def extractLocation(location:Config):Unit = {
    /*
     * Retrieve the weather data for the geo spatial
     * coordinates of the provided location
     */
    val lat = location.getDouble("lat")
    val lon = location.getDouble("lon")

    val weather = getByLatLon(lat, lon).getAsJsonObject
    /*
     * Send the weather data to the ThingsBoard
     * server; this request is implemented as a
     * device telemetry request. This implies that
     * each weather station is updated with its
     * own request.
     *
     * The format of the request:
     *
     * {
     *  "ts": 1527863043000,
     *  "values": {
     *    "temperature": 42.2,
     *    "humidity": 70,
     *    ...
     *  }
     * }
     */
    val message = new JsonObject
    /*
     * Weather stations leverage their own timestamp
     * and do not rely on the (received message) server
     * timestamps
     */
    val timestamp = weather.get("timestamp").getAsLong
    message.addProperty("ts", timestamp)

    val values = new JsonObject
    val fields = List(
      "temp",
      "feels_like",
      "temp_min",
      "temp_max",
      "humidity",
      "pressure",
      "wind_speed",
      "wind_deg",
      "wind_gust",
      "cloudiness",
      "visibility")

    fields.foreach(field => {
      val double_val = getDoubleWithScale(weather, field)
      values.addProperty(field, double_val)
    })

    message.add("values", values)
    /*
     * Finally, send message in ThingsBoard device format
     * to the server with device specific producer
     */
    val token = location.getString("token")
    if (producers.contains(token)) {
      producers(token).publish(message.toString)

    }
    else {
      val now = new java.util.Date()
      println(s"[WARN] $now.toString - TB Producer for token `$token` is not assigned.")

    }

  }

  def getByCityName(cityName:String):JsonElement = {

    val endpoint = s"${apiUrl}q={$cityName}&appid=$apiKey"

    val bytes = get(endpoint)
    val response = extractJsonBody(bytes)

    val weather = extractWeather(response.getAsJsonObject)
    weather

  }

  def getByLatLon(lat:Double, lon:Double):JsonElement = {

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
