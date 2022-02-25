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

import com.google.gson.{JsonArray, JsonObject}
import de.kp.works.things.http.HttpConnect
import de.kp.works.things.logging.Logging

import scala.collection.JavaConversions.iterableAsScalaIterable

case class OrsPoint(lat:Double, lon:Double)

class OrsRouting extends HttpConnect with Logging {

  private val authToken = MapOptions.getOrsToken
  private val baseUrl = MapOptions.getOrsUrl
  /**
   * The list of ORS movement profiles
   */
  private val orsProfiles = List(
    "driving-car",
    "driving-hgv",
    "cycling-regular",
    "cycling-road",
    "cycling-mountain",
    "cycling-electric",
    "foot-walking",
    "foot-hiking",
    "wheelchair"
  )
  /**
   * Access restrictions: 2.000 daily / 40 per minute
   *
   * NOTE: The coordinates provided must describe
   * (lon,lat) points
   */
  def directions(coordinates: JsonArray, profile: String = "foot-walking"): List[OrsPoint] = {

    if (!orsProfiles.contains(profile)) {
      warn(s"The provided profile `$profile` is not supported.")
      return List.empty[OrsPoint]
    }
    /*
     * The request leverages the pre-defined `geojson` format
     * to retrieve all the intermediate direction steps as
     * lon, lat coordinates (`json` provides way points)
     */
    val endpoint = s"$baseUrl/v2/directions/$profile/geojson"
    val headers = Map("Authorization" -> authToken, "Content-Type" -> "application/json")

    val body = new JsonObject
    body.add("coordinates", coordinates)

    val bytes = post(endpoint = endpoint, headers = headers, body.toString)
    val response = extractJsonBody(bytes)

    if (response == null) {
      warn(s"The ORS request does not return a valid JSON response.")
      return List.empty[OrsPoint]
    }

    /*
     * The result of the request is GeoJSON formatted. The current
     * implementation only extracts the geometry
     *
     * {
     *    "type":"FeatureCollection",
     *    "features":[
     *      {
     *        "bbox":[16.286224,48.194272,16.291814,48.196875],
     *        "type":"Feature",
     *        "properties":{
     *          "segments":[{"distance":695.5,"duration":64.4,"steps":[{"distance":271.5,"duration":24.6,"type":11,"instruction":"Head south on Seckendorfstraße","name":"Seckendorfstraße","way_points":[0,10]},{"distance":424.0,"duration":39.8,"type":0,"instruction":"Turn left onto Linzer Straße","name":"Linzer Straße","way_points":[10,29]},{"distance":0.0,"duration":0.0,"type":10,"instruction":"Arrive at Linzer Straße, straight ahead","name":"-","way_points":[29,29]}]}],"summary":{"distance":695.5,"duration":64.4},"way_points":[0,29]},"geometry":{"coordinates":[[16.287095,48.196875],[16.287091,48.196866],[16.287085,48.19685],[16.287056,48.196781],[16.286725,48.196041],[16.28635,48.195004],[16.286236,48.194698],[16.286234,48.194638],[16.286232,48.194594],[16.286229,48.19457],[16.286224,48.194509],[16.28636,48.194524],[16.286811,48.194583],[16.286974,48.194619],[16.287206,48.194688],[16.287438,48.194705],[16.287647,48.194711],[16.287885,48.194714],[16.288081,48.19471],[16.289112,48.194682],[16.289284,48.194633],[16.289372,48.194616],[16.289872,48.194577],[16.290008,48.194567],[16.290204,48.194544],[16.290658,48.194475],[16.290852,48.194475],[16.291587,48.194313],[16.291809,48.194272],[16.291814,48.194272]],"type":"LineString"}}],"bbox":[16.286224,48.194272,16.291814,48.196875],"metadata":{"attribution":"openrouteservice.org | OpenStreetMap contributors","service":"routing","timestamp":1645720189629,"query":{"coordinates":[[16.28709377819291,48.1968750461956],[16.29181446608709,48.1942718144596]],"profile":"driving-car","format":"geojson"},"engine":{"version":"6.7.0","build_date":"2022-02-18T19:37:41Z","graph_date":"2022-02-15T01:18:04Z"}}}
     *
     */
    val features = response.getAsJsonObject
      .get("features").getAsJsonArray

    val result = features.flatMap(feature => {

      val geometryObj = feature.getAsJsonObject
        .get("geometry").getAsJsonObject

      val coordinates = geometryObj.get("coordinates")
        .getAsJsonArray

      coordinates.map(coordinate => {

        val lon = coordinate.getAsJsonArray.get(0).getAsDouble
        val lat = coordinate.getAsJsonArray.get(1).getAsDouble

        OrsPoint(lat, lon)

      }).toList

    }).toList

    result

  }
  /**
   * Isochrones e.g. can be used to inform smartphone
   * users about the reachability of a certain entity
   * within a specific amount of time:
   *
   * "Can I reach this entity within 15 minutes?"
   *
   * Access restrictions: 500 daily / 20 per minute
   *
   * `time` is defined in seconds: 900 = 15 minutes
   */
  def isochrones(lat:Double, lon:Double, time:Int=900, profile:String="foot-walking"):Unit = {

    val endpoint = s"${baseUrl}/v2/isochrones/$profile"
    val headers = Map("Authorization" -> authToken, "Content-Type" -> "application/json")

    val body = new JsonObject()

    /* Locations
     *
     * Note ORS requires (lon,lat) pairs
     */
    val locations = new JsonArray()

    val coords = new JsonArray()
    coords.add(lon)
    coords.add(lat)

    locations.add(coords)
    body.add("locations", locations)

    /* Range */
    val range = new JsonArray()

    range.add(time)
    body.add("range", range)

    /* Range type = time */
    body.addProperty("range_type", "time")

    val query = body.toString

    val bytes = post(endpoint=endpoint, headers=headers, query=query)
    val response = extractJsonBody(bytes)
    /*
			{"type":"FeatureCollection","bbox":[11.564167,48.127939,11.597908,48.150232],"features":[{"type":"Feature","properties":{"group_index":0,"value":900.0,"center":[11.581200021892036,48.138958018765415]},"geometry":{"coordinates":[[[11.564167,48.1414],[11.564644,48.138633],[11.564674,48.138545],[11.567567,48.133302],[11.568314,48.131916],[11.569554,48.130142],[11.574549,48.128724],[11.577655,48.128208],[11.580838,48.127939],[11.583582,48.128256],[11.587695,48.129077],[11.589492,48.129311],[11.58956,48.129348],[11.593521,48.13249],[11.596417,48.133255],[11.59646,48.133309],[11.597845,48.135334],[11.597908,48.135435],[11.59768,48.138835],[11.59597,48.140788],[11.59366,48.143739],[11.592681,48.146068],[11.589235,48.147931],[11.586974,48.150036],[11.584375,48.150232],[11.583427,48.150091],[11.578303,48.14903],[11.575818,48.148306],[11.573782,48.147566],[11.569583,48.146369],[11.567745,48.145651],[11.56726,48.145233],[11.564184,48.141456],[11.564167,48.1414]]],"type":"Polygon"}}],"metadata":{"attribution":"openrouteservice.org | OpenStreetMap contributors","service":"isochrones","timestamp":1624106893847,"query":{"locations":[[11.581189870005808,48.13893527503447]],"range":[900.0],"range_type":"time"},"engine":{"version":"6.5.0","build_date":"2021-05-17T12:08:50Z","graph_date":"2021-06-02T13:43:46Z"}}}
     */
    println(response)

  }
  /**
   * Access restrictions: 1000 daily / 100 per minute
   */
  def reverseGeocode(lat:Double, lon:Double): Unit = {

    val endpoint = s"${baseUrl}/geocode/reverse?api_key=$authToken&point.lon=$lon&point.lat=$lat"

    val bytes = get(endpoint)
    val response = extractJsonBody(bytes)
    /*
     * {"geocoding":{
     * 	"version":"0.2",
     * 	"attribution":"https://openrouteservice.org/terms-of-service/#attribution-geocode",
     * 	"query":{
     * 		"size":10,
     * 		"private":false,
     * 		"point.lat":48.20571,
     * 		"point.lon":16.45779,
     * 		"boundary.circle.lat":48.20571,
     * 		"boundary.circle.lon":16.45779,
     * 		"lang":{
     * 			"name":"English",
     * 			"iso6391":"en",
     * 			"iso6393":"eng",
     * 			"via":"default",
     * 			"defaulted":true
     * 		},
     * 		"querySize":20
     * 	},
     * 	"engine":{
     * 		"name":"Pelias",
     * 		"author":"Mapzen",
     * 		"version":"1.0"
     * 	},
     * 	"timestamp":1624101991564
     * },
     * "type":"FeatureCollection",
     * "features":[
     * 	{
     * 		"type":"Feature",
     * 		"geometry":{
     * 			"type":"Point",
     * 			"coordinates":[16.45857,48.205399]
     * 		},
     * 		"properties":{
     * 			"id":"way/564825147",
     * 			"gid":"openstreetmap:address:way/564825147",
     * 			"layer":"address",
     * 			"source":"openstreetmap",
     * 			"source_id":"way/564825147",
     * 			"name":"Schillochweg 43",
     * 			"housenumber":"43",
     * 			"street":"Schillochweg",
     * 			"postalcode":"1220",
     * 			"confidence":0.8,
     * 			"distance":0.067,
     * 			"accuracy":"point",
     * 			"country":"Austria",
     * 			"country_gid":"whosonfirst:country:85632785",
     * 			"country_a":"AUT",
     * 			"region":"Wien",
     * 			"region_gid":"whosonfirst:region:85681667",
     * 			"region_a":"WI",
     * 			"localadmin":"Wien",
     * 			"localadmin_gid":"whosonfirst:localadmin:1108838101",
     * 			"locality":"Vienna",
     * 			"locality_gid":"whosonfirst:locality:101748073",
     * 			"neighbourhood":"Praterbrucke",
     * 			"neighbourhood_gid":"whosonfirst:neighbourhood:85898375",
     * 			"continent":"Europe",
     * 			"continent_gid":"whosonfirst:continent:102191581",
     * 			"label":"Schillochweg 43, Vienna, WI, Austria"
     * 		}
     * 	},
     * 	{
     * 		"type":"Feature",
     * 		"geometry":{"type":"Point","coordinates":[16.456997,48.206011]},
     * 		"properties":{
     * 			"id":"node/4429458565",
     * 			"gid":"openstreetmap:address:node/4429458565",
     * 			"layer":"address","source":"openstreetmap",
     * 			"source_id":"node/4429458565","name":"Otto-Weber-Gasse 37",
     * 			"housenumber":"37","street":"Otto-Weber-Gasse",
     * 			"postalcode":"1220",
     * 			"confidence":0.8,
     * 			"distance":0.068,
     * 			"accuracy":"point",
     * 			"country":"Austria",
     * 			"country_gid":"whosonfirst:country:85632785",
     * 			"country_a":"AUT",
     * 			"region":"Wien",
     * 			"region_gid":"whosonfirst:region:85681667",
     * 			"region_a":"WI",
     * 			"localadmin":"Wien",
     * 			"localadmin_gid":"whosonfirst:localadmin:1108838101",
     * 			"locality":"Vienna",
     * 			"locality_gid":"whosonfirst:locality:101748073",
     * 			"neighbourhood":"Praterbrucke",
     * 			"neighbourhood_gid":"whosonfirst:neighbourhood:85898375",
     * 			"continent":"Europe",
     * 			"continent_gid":"whosonfirst:continent:102191581",
     * 			"label":"Otto-Weber-Gasse 37, Vienna, WI, Austria"
     * 		}
     * 	},{
     * 		"type":"Feature",
     * 		"geometry":{
     * 			"type":"Point",
     * 			"coordinates":[16.457036,48.206057]
     * 		},
     * 		"properties":{
     * 			"id":"node/4429458566",
     * 			"gid":"openstreetmap:address:node/4429458566",
     * 			"layer":"address","source":"openstreetmap",
     * 			"source_id":"node/4429458566",
     * 			"name":"Otto-Weber-Gasse 39",
     * 			"housenumber":"39",
     * 			"street":"Otto-Weber-Gasse",
     * 			"postalcode":"1220",
     * 			"confidence":0.8,
     * 			"distance":0.068,
     * 			"accuracy":"point",
     * 			"country":"Austria",
     * 			"country_gid":"whosonfirst:country:85632785",
     * 			"country_a":"AUT",
     * 			"region":"Wien",
     * 			"region_gid":"whosonfirst:region:85681667",
     * 			"region_a":"WI",
     * 			"localadmin":"Wien",
     * 			"localadmin_gid":"whosonfirst:localadmin:1108838101",
     * 			"locality":"Vienna",
     * 			"locality_gid":"whosonfirst:locality:101748073",
     * 			"neighbourhood":"Praterbrucke",
     * 			"neighbourhood_gid":"whosonfirst:neighbourhood:85898375",
     * 			"continent":"Europe",
     * 			"continent_gid":"whosonfirst:continent:102191581",
     * 			"label":"Otto-Weber-Gasse 39, Vienna, WI, Austria"
     * 		}
     * 	},
     * 	{"type":"Feature","geometry":{"type":"Point","coordinates":[16.456949,48.205959]},
     * 	"properties":{"id":"node/4429458564","gid":"openstreetmap:address:node/4429458564","layer":"address","source":"openstreetmap","source_id":"node/4429458564","name":"Otto-Weber-Gasse 35","housenumber":"35",
     * 		"street":"Otto-Weber-Gasse","postalcode":"1220","confidence":0.8,"distance":0.068,"accuracy":"point","country":"Austria","country_gid":"whosonfirst:country:85632785","country_a":"AUT","region":"Wien",
     * 		"region_gid":"whosonfirst:region:85681667","region_a":"WI","localadmin":"Wien","localadmin_gid":"whosonfirst:localadmin:1108838101","locality":"Vienna","locality_gid":"whosonfirst:locality:101748073",
     * 		"neighbourhood":"Praterbrucke","neighbourhood_gid":"whosonfirst:neighbourhood:85898375","continent":"Europe","continent_gid":"whosonfirst:continent:102191581","label":"Otto-Weber-Gasse 35, Vienna, WI, Austria"}},
     * 		{"type":"Feature","geometry":{"type":"Point","coordinates":[16.457086,48.20611]},"properties":{"id":"node/4429458567","gid":"openstreetmap:address:node/4429458567","layer":"address","source":"openstreetmap",
     * 		"source_id":"node/4429458567","name":"Otto-Weber-Gasse 41","housenumber":"41","street":"Otto-Weber-Gasse","postalcode":"1220","confidence":0.8,"distance":0.069,"accuracy":"point","country":"Austria",
     * 		"country_gid":"whosonfirst:country:85632785","country_a":"AUT","region":"Wien","region_gid":"whosonfirst:region:85681667","region_a":"WI","localadmin":"Wien","localadmin_gid":"whosonfirst:localadmin:1108838101","locality":"Vienna","locality_gid":"whosonfirst:locality:101748073","neighbourhood":"Praterbrucke","neighbourhood_gid":"whosonfirst:neighbourhood:85898375","continent":"Europe","continent_gid":"whosonfirst:continent:102191581","label":"Otto-Weber-Gasse 41, Vienna, WI, Austria"}},{"type":"Feature","geometry":{"type":"Point","coordinates":[16.457122,48.206148]},"properties":{"id":"node/4429458568","gid":"openstreetmap:address:node/4429458568","layer":"address","source":"openstreetmap","source_id":"node/4429458568","name":"Otto-Weber-Gasse 43","housenumber":"43","street":"Otto-Weber-Gasse","postalcode":"1220","confidence":0.8,"distance":0.07,"accuracy":"point","country":"Austria","country_gid":"whosonfirst:country:85632785","country_a":"AUT","region":"Wien","region_gid":"whosonfirst:region:85681667","region_a":"WI","localadmin":"Wien","localadmin_gid":"whosonfirst:localadmin:1108838101","locality":"Vienna","locality_gid":"whosonfirst:locality:101748073","neighbourhood":"Praterbrucke","neighbourhood_gid":"whosonfirst:neighbourhood:85898375","continent":"Europe","continent_gid":"whosonfirst:continent:102191581","label":"Otto-Weber-Gasse 43, Vienna, WI, Austria"}},{"type":"Feature","geometry":{"type":"Point","coordinates":[16.456887,48.205894]},"properties":{"id":"node/4429458563","gid":"openstreetmap:address:node/4429458563","layer":"address","source":"openstreetmap","source_id":"node/4429458563","name":"Otto-Weber-Gasse 33","housenumber":"33","street":"Otto-Weber-Gasse","postalcode":"1220","confidence":0.8,"distance":0.07,"accuracy":"point","country":"Austria","country_gid":"whosonfirst:country:85632785","country_a":"AUT","region":"Wien","region_gid":"whosonfirst:region:85681667","region_a":"WI","localadmin":"Wien","localadmin_gid":"whosonfirst:localadmin:1108838101","locality":"Vienna","locality_gid":"whosonfirst:locality:101748073","neighbourhood":"Praterbrucke","neighbourhood_gid":"whosonfirst:neighbourhood:85898375","continent":"Europe","continent_gid":"whosonfirst:continent:102191581","label":"Otto-Weber-Gasse 33, Vienna, WI, Austria"}},{"type":"Feature","geometry":{"type":"Point","coordinates":[16.457162,48.2062]},"properties":{"id":"node/4429458569","gid":"openstreetmap:address:node/4429458569","layer":"address","source":"openstreetmap","source_id":"node/4429458569","name":"Otto-Weber-Gasse 45","housenumber":"45","street":"Otto-Weber-Gasse","postalcode":"1220","confidence":0.8,"distance":0.072,"accuracy":"point","country":"Austria","country_gid":"whosonfirst:country:85632785","country_a":"AUT","region":"Wien","region_gid":"whosonfirst:region:85681667","region_a":"WI","localadmin":"Wien","localadmin_gid":"whosonfirst:localadmin:1108838101","locality":"Vienna","locality_gid":"whosonfirst:locality:101748073","neighbourhood":"Praterbrucke","neighbourhood_gid":"whosonfirst:neighbourhood:85898375","continent":"Europe","continent_gid":"whosonfirst:continent:102191581","label":"Otto-Weber-Gasse 45, Vienna, WI, Austria"}},{"type":"Feature","geometry":{"type":"Point","coordinates":[16.456842,48.205838]},"properties":{"id":"node/4429458562","gid":"openstreetmap:address:node/4429458562","layer":"address","source":"openstreetmap","source_id":"node/4429458562","name":"Otto-Weber-Gasse 31","housenumber":"31","street":"Otto-Weber-Gasse","postalcode":"1220","confidence":0.8,"distance":0.072,"accuracy":"point","country":"Austria","country_gid":"whosonfirst:country:85632785","country_a":"AUT","region":"Wien","region_gid":"whosonfirst:region:85681667","region_a":"WI","localadmin":"Wien","localadmin_gid":"whosonfirst:localadmin:1108838101","locality":"Vienna","locality_gid":"whosonfirst:locality:101748073","neighbourhood":"Praterbrucke","neighbourhood_gid":"whosonfirst:neighbourhood:85898375","continent":"Europe","continent_gid":"whosonfirst:continent:102191581","label":"Otto-Weber-Gasse 31, Vienna, WI, Austria"}},{"type":"Feature","geometry":{"type":"Point","coordinates":[16.456811,48.205801]},"properties":{"id":"node/4429458560","gid":"openstreetmap:address:node/4429458560","layer":"address","source":"openstreetmap","source_id":"node/4429458560","name":"Otto-Weber-Gasse 29","housenumber":"29","street":"Otto-Weber-Gasse","postalcode":"1220","confidence":0.8,"distance":0.073,"accuracy":"point","country":"Austria","country_gid":"whosonfirst:country:85632785","country_a":"AUT","region":"Wien","region_gid":"whosonfirst:region:85681667","region_a":"WI","localadmin":"Wien","localadmin_gid":"whosonfirst:localadmin:1108838101","locality":"Vienna","locality_gid":"whosonfirst:locality:101748073","neighbourhood":"Praterbrucke","neighbourhood_gid":"whosonfirst:neighbourhood:85898375","continent":"Europe","continent_gid":"whosonfirst:continent:102191581","label":"Otto-Weber-Gasse 29, Vienna, WI, Austria"}}],"bbox":[16.456811,48.205399,16.45857,48.2062]}
     *
     */

    println(response)

  }

}