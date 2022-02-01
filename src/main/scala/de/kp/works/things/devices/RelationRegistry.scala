package de.kp.works.things.devices

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

import java.io.FileWriter
import scala.collection.mutable

case class RelationEntry(
  /*
   * The data source (origin) of this repository
   * item. Supported values are `airq`, `owea` and
   * `ttn`
   */
  datasource:String,
  /*
   * The ThingsBoard asset identifier that is used
   * to describe the `from` entity
   */
  tbFromId:String,
  /*
   * The ThingsBoard asset name that is used to
   * describe the `from` entity
   */
  tbFromName:String,
  /*
   * The ThingsBoard device identifiers that are
   * assigned as `to`s to the `from` identifier
   */
  tbToIds:List[String]

) {

  override def toString:String = {

    val values = Seq(datasource, tbFromId, tbFromName) ++ tbToIds
    values.mkString(",")

  }
}

object RelationRegistry {

  private var instance:Option[RelationRegistry] = None

  def getInstance:RelationRegistry = {

    if (instance.isEmpty) instance = Some(new RelationRegistry())
    instance.get

  }
}


class RelationRegistry {

  private val folder = RepositoryOptions.getFolder
  private val registry = mutable.HashMap.empty[String, RelationEntry]
  /**
   * This methods loads all available relations
   * into the internal repository
   */
  def load():Unit = {

    registry.clear()

    val filePath = folder + "relations.csv"
    val source = scala.io.Source
      .fromFile(new java.io.File(filePath))

    val relations = source.getLines
    relations.foreach(relation => {

      val tokens = relation.split(",")

      val datasource = tokens.head

      val tbFromId   = tokens(1)
      val tbFromName = tokens(2)

      val tbToIds =
        (3 until tokens.length).map(i => tokens(i)).toList

      val relationEntry =
        RelationEntry(datasource, tbFromId, tbFromName, tbToIds)

      registry += relationEntry.tbFromName -> relationEntry

    })

    source.close

  }
  def get(tbFromName:String):Option[RelationEntry] =
    registry.get(tbFromName)

  def register(entry:RelationEntry):Unit = {

    registry += entry.tbFromName -> entry
    /*
     * Append repository entry to repository file;
     * note, the registry process runs sequentially.
     *
     * Therefore, there is no need to do concurrency
     * control.
     */
    val filePath = folder + "relations.csv"
    val writer = new FileWriter(filePath, true)

    val line = entry.toString + "\n"

    writer.write(line)
    writer.close()

  }
}
