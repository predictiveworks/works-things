package de.kp.works.things.logging

import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.{ILoggingEvent, LoggingEvent}
import ch.qos.logback.core.encoder.Encoder
import ch.qos.logback.core.rolling.{RollingFileAppender, SizeAndTimeBasedRollingPolicy, TimeBasedRollingPolicy}
import ch.qos.logback.core.util.FileSize
import de.kp.works.things.ThingsConf
import org.slf4j.LoggerFactory

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

object ThingsLogger {

  /**
   * The internal configuration is used, if the current
   * configuration is not set here
   */
  if (!ThingsConf.isInit) ThingsConf.init()
  /**
   * Determine the registry folder from the system
   * property `logging.dir`
   */
  private val folder = System.getProperty("logging.dir")
  private val loggingCfg = ThingsConf.getLoggingCfg

  private val logger = buildLogger

  def getLogger:Logger = logger

  private def getFolder:String = {

    if (folder == null)
      loggingCfg.getString("folder")

    else folder


  }
  /**
   * This method build the Logback logger including
   * a rolling file appender programmatically
   */
  private def buildLogger:Logger = {

    val logCtx = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

    /*
     * Build encoder and use default pattern
     */
    val logEncoder = new PatternLayoutEncoder()
    logEncoder.setContext(logCtx)

    logEncoder.setPattern(" %d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n")
    logEncoder.start()
    /*
     * Build rolling file appender
     */
    val logFileAppender = new RollingFileAppender[ILoggingEvent]()
    logFileAppender.setContext(logCtx)

    logFileAppender.setName("logFileAppender")
    logFileAppender.setEncoder(logEncoder.asInstanceOf[Encoder[ILoggingEvent]])

    logFileAppender.setAppend(true)
    logFileAppender.setFile(s"${getFolder}things.log");
    /*
     * Set time- and size-based rolling policy
     */
    val logFilePolicy = new SizeAndTimeBasedRollingPolicy[ILoggingEvent]()
    logFilePolicy.setContext(logCtx)

    logFilePolicy.setParent(logFileAppender)
    logFilePolicy.setFileNamePattern(s"${getFolder}things.%d{yyyy-MM-dd}.%i.log")

    logFilePolicy.setMaxFileSize(FileSize.valueOf("100mb"))
    logFilePolicy.setTotalSizeCap(FileSize.valueOf("3GB"))

    logFilePolicy.setMaxHistory(30)
    logFilePolicy.start()

    logFileAppender.setRollingPolicy(logFilePolicy)
    logFileAppender.start()
    /*
     * Finally builder logger
     */
    val logger = logCtx.getLogger("Things")
    logger.setAdditive(false)

    logger.setLevel(Level.INFO)
    logger.addAppender(logFileAppender)

    logFileAppender.start()
    logger

  }
}
