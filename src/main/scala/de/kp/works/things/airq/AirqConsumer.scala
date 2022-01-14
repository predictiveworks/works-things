package de.kp.works.things.airq

import de.kp.works.things.http.HttpConnect
import de.kp.works.things.weather.WeatherOptions

class AirqConsumer extends HttpConnect {

  private val apiUrl = WeatherOptions.getBaseUrl
  private val apiInterval = 1000 * 60 * 30

  def start(): Unit = ???

  def stop(): Unit = ???

  def extract(country:String, pollutant:String):Unit = ???
}


