package de.kp.works.things.image

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

import de.kp.works.things.conf.ThingsConf
import org.knowm.xchart.{XYChart, XYChartBuilder}
import org.knowm.xchart.style.markers.SeriesMarkers

import java.awt.Color

abstract class ImageMaker {

  /**
   * The internal configuration is used, if the current
   * configuration is not set here
   */
  if (!ThingsConf.isInit) ThingsConf.init()
  /**
   * Determine the images folder from the system
   * property `images.dir`
   */
  private val folder = System.getProperty("images.dir")
  private val imagesCfg = ThingsConf.getImagesCfg
  /**
   * The dimensions of the image
   */
  private val WIDTH  = 1200
  private val HEIGHT = 600
  /**
   * The chart padding
   */
  private val PADDING = 30

  private val BG_COLOR   = new Color(255, 255, 255)
  private val FONT_COLOR = new Color(41, 48, 66)
  private val LINE_COLOR = new Color(41, 48, 66)

  /**
   * This method an XYChart line chart from a
   * timeseries (index, timestamp, value)
   */
  def buildChartTs(title:String, values:List[(Double, Long, Double)]): XYChart = {
    /*
     * The axis labels are not specified as the
     * respective context is defined
     */
    val chart = new XYChartBuilder().width(WIDTH).height(HEIGHT)
      .title(title)
      .xAxisTitle("").yAxisTitle("")
      .build
    /*
     * Prepare data as x, y and date lookup series
     */
    val xData = values.map(_._1).toArray
    val yData = values.map(_._3).toArray

    val lookup = values
      .map { case (index, ts, _) => (index, formatTs(ts)) }
      .toMap
    /*
     * Build timeseries, smooth respective line
     * and remove all point markers
     */
    val series = chart.addSeries(" ", xData, yData)
    series.setSmooth(true)
    series.setMarker(SeriesMarkers.NONE)
    /*
     * Define the timestamp formatter, i.e. a mapper from
     * the provided x value (index) and its label (date)
     */
    val formatter = new java.util.function.Function[java.lang.Double, String] {
      override def apply(index: java.lang.Double): String = {
        lookup(index)
      }

    }

    chart.setCustomXAxisTickLabelsFormatter(formatter)
    chart.getStyler.setLegendVisible(false)

    chart.getStyler.setChartBackgroundColor(BG_COLOR)
    chart.getStyler.setChartFontColor(FONT_COLOR)

    chart.getStyler.setSeriesColors(Array[Color](LINE_COLOR))
    /*
     * This flag removes the double line, x, and y
     * axis from the respective image
     */
    chart.getStyler.setAxisTicksLineVisible(false)
    /*
     * Set the overall chart padding to leave some
     * space between the border of the image and the
     * chart
     */
    chart.getStyler.setChartPadding(PADDING)
    chart

  }

  def formatTs(ts:Long):String

  def getFolder:String = {

    if (folder == null)
      imagesCfg.getString("folder")

    else folder

  }

}
