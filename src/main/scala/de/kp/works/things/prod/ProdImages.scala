package de.kp.works.things.prod

import de.kp.works.things.ThingsConf
import de.kp.works.things.devices.DeviceRegistry
import de.kp.works.things.logging.Logging
import de.kp.works.things.tb.{TBAdmin, TBPoint}
import de.kp.works.things.ttn.{TTNAdmin, TTNDevice}
import org.knowm.xchart.BitmapEncoder.BitmapFormat
import org.knowm.xchart.style.markers.SeriesMarkers
import org.knowm.xchart.{BitmapEncoder, XYChart, XYChartBuilder}

import java.awt.Color
import java.time.{Instant, LocalDateTime}
import java.util.{Calendar, TimeZone}
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import scala.collection.JavaConversions._
import scala.collection.mutable

class ProdCharts(numThreads:Int = 1) extends Logging {
  /**
   * The configured time interval the image maker
   * task is executed
   */
  private val interval:Long = 1000 * 60 * 60 * 12
  private val initialDelay = getInitialDelay

  private var executorService:ScheduledExecutorService = _
  private val imageMaker = new ProdImages()

  def start():Unit = {

    val worker = new Runnable {

      override def run(): Unit = {
        info(s"Prod Image Maker started.")
        imageMaker.buildImages()
      }
    }

    try {

      executorService = Executors.newScheduledThreadPool(numThreads)
      executorService.scheduleAtFixedRate(worker, initialDelay, interval, TimeUnit.MILLISECONDS)

    } catch {
      case t:Exception =>
        error(s"Prod Image Maker failed with: ${t.getLocalizedMessage}")
        stop()
    }

  }

  def stop():Unit = {

    executorService.shutdown()
    executorService.shutdownNow()

  }
  /**
   * Images from the respective sensor time series
   * are created at 6:00 (AM) and 18:00 (PM).
   *
   * This method computes the difference in milli
   * seconds from now to the next creation date
   */
  private def getInitialDelay:Long = {
    /*
     * Determine the current timestamp
     * in milliseconds
     */
    val cal = Calendar.getInstance
    val now = cal.getTimeInMillis

    val hour = cal.get(Calendar.HOUR_OF_DAY)
    /*
     * Normalize milli seconds, seconds
     * and minutes of the day
     */
    cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MINUTE, 0)

    if (hour < 6) {
      /*
       * The current timestamp is between
       * [00:00 and 06:00[, so we reset to
       * the 00:00 and add to 6 hours
       */
      cal.set(Calendar.HOUR_OF_DAY, 6)
      val next = cal.getTimeInMillis

      val duration = next - now
      return duration

    }

    if (hour >= 6 && hour < 18) {
      /*
       * The current timestamp is between
       * [06:00 and 18:00[, so we reset to
       * the 00:00 and add 18 hours
       */
      cal.set(Calendar.HOUR_OF_DAY, 18)
      val next = cal.getTimeInMillis

      val duration = next - now
      return duration

    } else if (hour > 18) {
      /*
       * The current timestamp is between
       * [18:00 and 23:59[, so we reset to
       * the 00:00 of the next day and add
       * 6 hours
       */
      cal.add(Calendar.DAY_OF_MONTH, 1)
      cal.set(Calendar.HOUR_OF_DAY, 6)
      val next = cal.getTimeInMillis

      val duration = next - now
      return duration

    }

    0L

  }

}

/**
 * This object is introduced to transform the last 24 hours
 * of measurements from the respective production stations
 * to Slack
 */
class ProdImages extends Logging {

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

  private val rooms = ProdOptions.getRooms
  private val ttnDevices = getTTNDevices

  private val WIDTH  = 800
  private val HEIGHT = 600

  private val BG_COLOR   = new Color(255, 255, 255)
  private val FONT_COLOR = new Color(41, 48, 66)
  private val LINE_COLOR = new Color(41, 48, 66)

  private def getFolder:String = {

    if (folder == null)
      imagesCfg.getString("folder")

    else folder

  }

  def buildImages():Unit = {
    /*
     * The current implementation generates a *.png (multiline)
     * image for each production room
     */
    rooms.foreach(room => {

      val tbAssetName = room.id
      /*
       * Retrieve attribute mapping of the specific room;
       * this encloses all sensors (attributes) tha refer
       * to all devices associated with the room asset
       */
      val lookup = ProdOptions.getMappingsByAsset(tbAssetName)
        .mappings.map(m =>
          (m.sensor, s"${m.label} (${m.units})")).toMap

      /*
       * The title is built from station and room
       * name
       */
      val roomTitle = s"${room.station}: ${room.name}"
      val roomCharts = mutable.ArrayBuffer.empty[XYChart]
      /*
       * Fill the respective room charts, which will
       * be joined into a single image
       */
      ttnDevices(tbAssetName).foreach(ttnDevice => {
        /*
         * This request retrieves the values of the
         * last 24 hours
         */
        val tbAdmin = new TBAdmin()
        if (!tbAdmin.login())
          throw new Exception("Login to ThingsBoard failed.")
        /*
         * Retrieve the registry entry that refers
         * to the TTN device identifier
         */
        val deviceEntry = DeviceRegistry.getByTTNDeviceId(ttnDevice.device_id)
        if (deviceEntry.nonEmpty) {

          val tbDeviceId = deviceEntry.get.tbDeviceId
          /* ------------------------------
           *
           *     GET CLIENT ATTRIBUTES
           *
           */
          val tbKeys = tbAdmin.getTsKeys(tbDeviceId)
          Thread.sleep(10)
          /*
           * The timeseries contains all sensors (keys)
           * that are assigned to the specific device
           */
          val tbValues =
            getDeviceTs(tbAdmin, tbDeviceId, tbKeys)
          /*
           * Each `sensor` of the specific device is
           * visualized in its own chart
           */
          tbValues.foreach{case(sensor, values) =>
            val chartTitle = roomTitle + " - " + lookup(sensor)
            val chartValues = values.zipWithIndex
              .map{case(point, index) => (index.toDouble, point.ts, point.value)}

            val roomChart = buildChart(chartTitle, chartValues)
            roomCharts += roomChart
          }
        }
        /*
         * Requests to ThingsBoard are finished,
         * so logout and prepare for next login
         */
        tbAdmin.logout()

      })
      /*
       * Finally build the multi-chart image for all
       * sensors that refer to a certain room
       */
      val path = s"$getFolder$tbAssetName.png"
      val (rows, cols) = {
        val length = roomCharts.length
        if (length == 1) {
          (1,1)
        } else {
          ((length / 2) + (length % 2), 2)
        }
      }

      BitmapEncoder.saveBitmap(roomCharts.toList, rows, cols, path, BitmapFormat.PNG)

    })

  }

  private def buildChart(title:String, values:List[(Double, Long, Double)]): XYChart = {
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
    chart

  }

  private def formatTs(ts:Long):String = {

    val date = LocalDateTime
      .ofInstant(Instant.ofEpochMilli(ts), TimeZone.getDefault.toZoneId)

    val hour = date.getHour
    val minute = date.getMinute

    val hourStr = if (hour < 10) s"0$hour" else s"$hour"
    val minuteStr = if (minute < 10) s"0$minute" else s"$minute"

    val formatted = s"$hourStr:$minuteStr"
    formatted

  }
  /**
   * This method retrieves the timeseries of all
   * attributes (sensors) that refer to a certain
   * device
   */
  private def getDeviceTs(tbAdmin:TBAdmin, tbDeviceId:String, tbKeys:Seq[String]):Map[String, List[TBPoint]] = {
    /*
     * The timeseries contains all sensors (keys)
     * that are assigned to the specific device
     */
    val tbValues = try {

      val cal = Calendar.getInstance
      cal.add(Calendar.DAY_OF_MONTH, -1)

      val startTs = cal.getTime.getTime
      val endTs = System.currentTimeMillis()

      val tbParams = Map("startTs" -> startTs.toString, "endTs" -> endTs.toString)
      val tbTimeseries = tbAdmin.getTsHistorical(
        deviceId = tbDeviceId, keys = tbKeys, params = tbParams, limit = 10000)

      tbTimeseries

    } catch {
      case t: Throwable =>
        error(s"${t.getLocalizedMessage}")
        Map.empty[String, List[TBPoint]]
    }

    tbValues

  }

  private def getTTNDevices:Map[String, Seq[TTNDevice]] = {

    val ttnAdmin = new TTNAdmin()
    try {

      val ttnDevices = ttnAdmin.getDevices
      ttnDevices.groupBy(ttnDevice => ttnDevice.asset)

    } catch {
      case _:Throwable => Map.empty[String, Seq[TTNDevice]]
    }

  }

}
