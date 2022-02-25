package de.kp.works.things.map

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

import com.google.gson.{JsonArray, JsonObject, JsonParser}

import scala.collection.JavaConversions.iterableAsScalaIterable

object OrsOSM {

  private val folder = "/Users/krusche/IdeaProjects/works-things/maps"
  /**
   * This method loads pre-built administrative boundaries
   * and transforms them into a polyline representation
   * including the surrounding region for visualization
   * on a `react-native-map`
   */
  def loadBoundary(name:String):String = {
    /*
     * The file defines a multigon extracted from
     * OpenStreetMap (OSM), specified as a single
     * line
     */
    val file = s"$folder/$name.csv"
    val source = scala.io.Source.fromFile(file)

    val line = source.getLines.next()

    val coordinates = new JsonArray
    /*
     * Transform line into a flat array of latlon
     * coordinates; this is due to the support of
     * the `react-native-map`
     */
    val json = JsonParser.parseString(line).getAsJsonArray
    json.foreach(e => extractCoords(e.getAsJsonArray, coordinates))
    /*
     * Compute the bounding box
     */
    val lats = coordinates.map(e => e.getAsJsonObject.get("latitude").getAsDouble).toArray
    val lons = coordinates.map(e => e.getAsJsonObject.get("longitude").getAsDouble).toArray

    val lat_min = lats.min
    val lat_max = lats.max

    val lon_min = lons.min
    val lon_max = lons.max
    /*
     * The scale factor `6` or `5` enables the correct
     * visualization
     */
    val region = new JsonObject
    region.addProperty("latitude", lat_min)
    region.addProperty("longitude", lon_min)

    region.addProperty("latitudeDelta", (lat_max - lat_min) * 5)
    region.addProperty("longitudeDelta", (lon_max - lon_min) * 5)

    val output = new JsonObject
    output.add("region", region)
    output.add("coordinates", coordinates)

    source.close
    output.toString

  }
  /**
   * This method loads pre-built geospatial assets
   * by name that refer to a certain administrative
   * boundary.
   */
  def loadAssets(name:String):String = {
    val file = s"$folder/$name-assets.csv"
    val source = scala.io.Source.fromFile(file)

    val lines = source.getLines
    val output = new JsonArray
    /*
     * __MOCK__ The code below assigns an attribute
     * `fill` to the pre-built assets,
     */
    val random = scala.util.Random
    lines.foreach(line => {

      val jsonObj = JsonParser.parseString(line).getAsJsonObject
      jsonObj.addProperty("fill", random.nextFloat())

      output.add(jsonObj)
    })

    source.close
    output.toString

  }
  /**
   * A helper method to walk through the multigon
   * representation of geospatial points and extract
   * the associated points
   */
  private def extractCoords(source:JsonArray, target:JsonArray):Unit = {

    source.foreach(e => {
      if (e.isJsonArray)
        extractCoords(e.getAsJsonArray, target)

      else if (e.isJsonObject) {
        target.add(e.getAsJsonObject)

      } else {
        /* Do nothing */
      }

    })
  }
}
