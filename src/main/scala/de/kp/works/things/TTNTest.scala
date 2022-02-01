package de.kp.works.things

import de.kp.works.things.ttn.TTNConsumer

object TTNTest {

  def main(args:Array[String]):Unit = {

    println("# ------------------------------")
    println("#")
    println("# Connect to The Things Network")
    println("#")
    println("# ------------------------------")
//
//    val mqttTopic = "v3/hutundstiel@ttn/devices/eui-70b3d57ed004b31f/up"
//    val consumer = new TTNConsumer().setTopic(mqttTopic)
//
//    try {
//
//      var consuming = true
//      val startTs = System.currentTimeMillis()
//
//      consumer.start()
//
//      while (consuming) {
//
//        val now = System.currentTimeMillis()
//        if (now - startTs > 15*1000) consuming = false
//
//      }
//
//    } catch {
//      case t: Throwable => t.printStackTrace()
//    }
//
//    consumer.stop()
//    Thread.sleep(500)

    println("Terminated")
    System.exit(0)

  }
}
