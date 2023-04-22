/**
 * Copyright 2023 Andreas Wagenmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format

import de.awagen.kolibri.datatypes.mutable.stores.TypedMapStore
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.types.NamedClassTyped
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.FileFormats.Parts.Parts
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable


/**
 * Composition of fileNames to simplify file based messaging
 */
object FileFormats {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  object Parts extends Enumeration {
    type Parts = NamedClassTyped[Any]

    val CREATION_TIME_IN_MILLIS: NamedClassTyped[Long] = NamedClassTyped("creationTimeInMillis")
    val TOPIC: NamedClassTyped[String] = NamedClassTyped("topic")
    val ID: NamedClassTyped[String] = NamedClassTyped("id")
    val BATCH_NR: NamedClassTyped[Int] = NamedClassTyped("batchNr")
  }

  trait FileFormat {

    def orderedParts: Seq[Parts]

    def partDelimiter: String

    def format(values: Seq[Any]): String

    def parse(name: String): TypeTaggedMap

  }

  case class FileNameFormat(orderedParts: Seq[Parts], partDelimiter: String = "_") extends FileFormat {

    /**
     * Checks whether each value corresponds to a specified part in orderedParts, and tests whether
     * casting to the needed type would work.
     * If so, construct the string value.
     */
    def format(values: Seq[Any]): String = {
      if (values.length != orderedParts.length) {
        throw new IllegalArgumentException(s"Number of values in '$values' does not match the number of needed parts '$orderedParts'")
      }
      try {
        values.indices.foreach(index => orderedParts(index).castFunc.apply(values(index)))
      }
      catch {
        case e: Exception =>
          logger.error(s"Passed values '$values' are not of right type as in specified parts '$orderedParts'", e)
          throw e
      }
      values.map(value => value.toString).mkString(partDelimiter)
    }

    /**
     * Parse the single parts from a filename
     */
    def parse(name: String): TypeTaggedMap = {
      val split: Seq[String] = name.split(partDelimiter).toSeq
      if (split.length != orderedParts.length) {
        throw new IllegalArgumentException(s"Number of values in name '$split' does not match the number of needed parts '$orderedParts'")
      }
      val resultMap: Map[Parts, String] = orderedParts.zip(split).toMap
      TypedMapStore(mutable.Map.newBuilder(resultMap).result())
    }

  }


}
