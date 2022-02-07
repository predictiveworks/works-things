package de.kp.works.things.slack

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

import com.slack.api.Slack
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.methods.request.files.FilesUploadRequest
import de.kp.works.things.logging.Logging

import scala.collection.JavaConversions._

class SlackBot extends Logging {

  private val slackChannel:String = SlackOptions.getChannel
  private val slackToken:String = SlackOptions.getOAuthToken

  private val slackApi = Slack.getInstance()
  private val methodsClient = slackApi.methods(slackToken)

  /**
   * This method sends text messages to the Hut & Stiel
   * Slack workspace
   */
  def message(message:String):Unit = {

    try {
      /*
       * The request builder pattern is used
       * to generate a chat message request
       */
      val request = ChatPostMessageRequest.builder()
        .channel(slackChannel)
        .text(message)
        .build()

      val response = methodsClient.chatPostMessage(request)
      if (!response.isOk) {
        warn(s"Sending a chat message to Slack failed: ${response.getError}")
      }

    } catch {
      case t:Throwable =>
       error(s"Sending a chat message to Slack failed: ${t.getLocalizedMessage}")
    }


  }
  /**
   * This method uploads a certain images to the
   * Hut & Stiel workspace
   */
  def upload(path:String, title:String):Unit = {

    try {

      val file = new java.io.File(path)
      if (!file.exists) {
        warn(s"The provided file `$path` does not exist.")

      } else {

        val fileName = file.getName
        info(s"Uploading image `$fileName` to Slack.")

        val request = FilesUploadRequest.builder()
          .channels(List(slackChannel))
          .file(file)
          .filename(fileName)
          .filetype("image/png")
          .title(title)
          .build()

        val response = slackApi.methods(slackToken).filesUpload(request)
        if (!response.isOk) {
          warn(s"Uploading image to Slack failed: ${response.getError}")
        }

      }

    } catch {
      case t:Throwable =>
        error(s"Uploading image to Slack failed: ${t.getLocalizedMessage}")

    }

  }

}
