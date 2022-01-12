package de.kp.works.things

import de.kp.works.things.thingsboard.TBProducer

object ThingsTest {

  private val deviceToken:String = ""

  def main(args:Array[String]):Unit = {

    println("# ------------------------------")
    println("#")
    println("# Connect to ThingsBoard")
    println("#")
    println("# ------------------------------")

    val producer = new TBProducer()
    try {
      producer.start(deviceToken)

    } catch {
      case t:Throwable => t.printStackTrace()
    }

    producer.stop()

    println("Terminated")
    System.exit(0)
  }
}
