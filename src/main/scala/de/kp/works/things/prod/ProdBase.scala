package de.kp.works.things.prod

import de.kp.works.things.ttn.{TTNAdmin, TTNDevice}

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

trait ProdBase {

  def buildTBDeviceName(deviceName:String, assetName:String):String = {

    val devicePrefix = s"DEV.${deviceName.replace(" ", "-").toUpperCase}"
    val tbDeviceName = {
      /*
       * Remove the prefix from the room identifier
       * and replace by the device prefix
       */
      val tokens = Array(devicePrefix) ++assetName.split("\\.").tail
      tokens.mkString(".")
    }

    tbDeviceName

  }

}
