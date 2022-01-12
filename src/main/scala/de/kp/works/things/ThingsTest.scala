package de.kp.works.things

import com.google.gson.JsonObject
import de.kp.works.things.thingsboard.TBProducer

object ThingsTest {

  private val deviceToken:String = "jKAAaVjfshCfm097OKVe"

  def main(args:Array[String]):Unit = {

    /*
     * Load NO2 data for Lobau
     */
    val path = "/Users/krusche/IdeaProjects/works-things/lobau_no2.csv"
    val file = new java.io.File(path)

    val source = scala.io.Source.fromFile(file)
    val lines = source.getLines.toList

    source.close

    println("# ------------------------------")
    println("#")
    println("# Connect to ThingsBoard")
    println("#")
    println("# ------------------------------")

    val producer = new TBProducer()
    try {

      producer.start(deviceToken)

      /*
       * Send data to MQTT server
       */
      lines.foreach(line => {

        val tokens = line.split(",")

        val timestamp = tokens.head.toLong
        val concentration = tokens(1).toDouble
        /*
         * Sample of how to use custom timestamp

        {
          "ts": 1527863043000,
          "values": {
            "temperature": 42.2,
            "humidity": 70
          }
        }
         *
         */
        val message = new JsonObject
        message.addProperty("ts", timestamp)

        val values = new JsonObject
        values.addProperty("concentration", concentration)

        message.add("values", values)

        producer.publish(message.toString)
        Thread.sleep(50)

      })

    } catch {
      case t:Throwable => t.printStackTrace()
    }

    producer.stop()

    println("Terminated")
    System.exit(0)
  }
}
