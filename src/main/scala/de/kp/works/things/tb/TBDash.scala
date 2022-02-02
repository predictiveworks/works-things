package de.kp.works.things.tb

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
import de.kp.works.things.logging.Logging
import org.thingsboard.server.common.data.Dashboard
import org.thingsboard.server.common.data.id.TenantId
import org.thingsboard.server.common.data.widget.{WidgetType, WidgetsBundle}

import scala.collection.immutable.HashMap

object TBDash {

  def main(args:Array[String]):Unit = {

    val tbDash = new TBDash()
    // tbDash.createDash("Test Dashboard")

    tbDash.login()
    /*
    val widgetType = tbDash.getWidgetTs
    println(widgetType.getId.getId)
    println(widgetType.getDescriptor)
    */

    val dashboard = tbDash.getDashboards
      .filter(d => d.getTitle == "Pilzproduktion")
      .head

    println(dashboard.getId.getId)
    tbDash.logout()
    System.exit(0)
  }

}

class TBDash extends TBClient with Logging {

  private val tenantId = adminCfg.getString("tenantId")
  private val customerId = adminCfg.getString("customerId")
  /**
   * REST endpoints used to create a dashboard
   * and assign the pre-defined customer
   */
  private val dashUrl = "/api/dashboard"
  private val dashboardsUrl = "/api/customer/$CUSTOMER/dashboards?page=0&pageSize=1000"

  private val assignUrl = "/api/customer/$CUSTOMER/dashboard/$DASHBOARD"
  /**
   * REST endpoints used to assign widgets
   * to a certain dashboard
   */
  private val widgetsBundlesUrl = "/api/widgetsBundles"
  /**
   * The current approach is restricted to the
   * provided widget types, i.e. isSystem = true
   *
   */
  private val widgetTypesUrl = "/api/widgetTypes?isSystem=true&bundleAlias="

  def getDashboards:List[Dashboard] = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + dashboardsUrl
      .replace("$CUSTOMER", customerId)

    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")
    val bytes = get(endpoint, header)

    val json = extractJsonBody(bytes)
    val data = json.getAsJsonObject.get("data").getAsJsonArray

    val dashboards = mapper.readValue(data.toString, classOf[List[_]])
    dashboards.map {
      case content: HashMap[_, _] =>
        mapper.convertValue(content, classOf[Dashboard])
      case _ =>
        throw new Exception("The content does not describe a [WidgetsBundle].")
    }

  }

  def createDash(title:String):String = {

    try {
      /*
       * STEP #1: Create dashboard instance
       * from provided title
       */
      val tbDashboard = new Dashboard()
      tbDashboard.setTitle(title)

      val tbTenantId = new TenantId(
        java.util.UUID.fromString(tenantId))

      tbDashboard.setTenantId(tbTenantId)
      /*
       * STEP #2: Login to ThingsBoard REST API
       * and retrieve access tokens for this session
       */
      if (!login())
        throw new Exception("Login to ThingsBoard failed.")
      /*
       * STEP #3: Create dashboard
       */
      val tbResponse = createDash(tbDashboard)
      val tbDashBoardId = extractEntityId(tbResponse)
      /*
       * STEP #4: Assign customer to this dashboard
       */
      assignCustomer(customerId, tbDashBoardId)
      /*
       * STEP Requests to ThingsBoard is finished,
       * so logout and prepare for next login
       */
      logout()
      /*
       * Return unique dashboard identifier for
       * further processing
       */
      tbDashBoardId

    } catch {
      case t:Throwable =>
        error(s"Creation of dashboard `$title` failed with: ${t.getLocalizedMessage}")
        null
    }

  }
  def assignCustomer(customerId:String, dashboardId:String):JsonElement = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + assignUrl
      .replace("$CUSTOMER", customerId)
      .replace("$DASHBOARD", dashboardId)

    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = post(endpoint, header, new JsonObject().toString)

    val json = extractJsonBody(bytes)
    json

  }

  def createDash(dash:Dashboard):JsonElement = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + dashUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = post(endpoint, header, mapper.writeValueAsString(dash))

    val json = extractJsonBody(bytes)
    json

  }

  def getChartsBundle:WidgetsBundle = {
    val bundles = getWidgetsBundles
    bundles.filter(bundle => bundle.getAlias == "charts").head
  }

  def getWidgetsBundles:List[WidgetsBundle] = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + widgetsBundlesUrl
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = get(endpoint, header)
    val json = extractJsonBody(bytes)

    val bundles = mapper.readValue(json.toString, classOf[List[_]])
    bundles.map {
      case content: HashMap[_, _] =>
        mapper.convertValue(content, classOf[WidgetsBundle])
      case _ =>
        throw new Exception("The content does not describe a [WidgetsBundle].")
    }

  }

  def getChartsWidgetTypes:List[WidgetType] = {

    if (authToken.isEmpty) {
      throw new Exception(s"No access token found to access ThingsBoard.")
    }

    val endpoint = baseUrl + widgetTypesUrl + "charts"
    val header = Map("X-Authorization"-> s"Bearer ${authToken.get}")

    val bytes = get(endpoint, header)
    val json = extractJsonBody(bytes)

    val widgetTypes = mapper.readValue(json.toString, classOf[List[_]])
    widgetTypes.map {
      case content: HashMap[_, _] =>
        mapper.convertValue(content, classOf[WidgetType])
      case _ =>
        throw new Exception("The content does not describe a [WidgetType].")
    }

  }

  def getWidgetTs:WidgetType = {

    val widgetTypes = getChartsWidgetTypes
    widgetTypes
      .filter(widgetType => widgetType.getAlias == "basic_timeseries")
      .head

  }
}
