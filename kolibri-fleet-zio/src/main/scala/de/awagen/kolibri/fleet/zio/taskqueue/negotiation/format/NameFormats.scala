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

import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore}
import de.awagen.kolibri.datatypes.types.NamedClassTyped
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.Parts.{BATCH_NR, CREATION_TIME_IN_MILLIS, ID, NODE_HASH, Parts, TOPIC}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable


/**
 * Composition of fileNames to simplify file based messaging
 */
object NameFormats {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  object Parts extends Enumeration {
    type Parts = NamedClassTyped[Any]

    val NODE_HASH: NamedClassTyped[String] = NamedClassTyped("nodeHash")
    val CREATION_TIME_IN_MILLIS: NamedClassTyped[Long] = NamedClassTyped("creationTimeInMillis")
    val TOPIC: NamedClassTyped[String] = NamedClassTyped("topic")
    val ID: NamedClassTyped[String] = NamedClassTyped("id")
    val BATCH_NR: NamedClassTyped[Int] = NamedClassTyped("batchNr")
  }

  trait NameFormat {

    def uniqueOrderedParts: Seq[Parts]

    def partDelimiter: String

    def format(values: TypeTaggedMap): String

    def parse(name: String): TypeTaggedMap

  }

  object NameFormat {

    def getCreationTimeInMillis(map: TypeTaggedMap): Option[Long] = map.get(CREATION_TIME_IN_MILLIS)
    def getTopic(map: TypeTaggedMap): Option[String] = map.get(TOPIC)
    def getId(map: TypeTaggedMap): Option[String] = map.get(ID)
    def getBatchNr(map: TypeTaggedMap): Option[Int] = map.get(BATCH_NR)
    def getNodeHash(map: TypeTaggedMap): Option[String] = map.get(NODE_HASH)

  }

  /**
   * Specify the used ordering of the added parts.
   */
  case class FileNameFormat(orderedParts: Seq[Parts], partDelimiter: String = "_") extends NameFormat {

    val uniqueOrderedParts: Seq[Parts] = orderedParts.distinct

    /**
     * Checks whether each value corresponds to a specified part in orderedParts, and tests whether
     * casting to the needed type would work.
     * If so, construct the string value.
     */
    def format(values: TypeTaggedMap): String = {
      if (uniqueOrderedParts.exists(part => !values.keys.toSeq.contains(part))) {
        throw new IllegalArgumentException(s"Values in '$values' do not contain all needed parts '$uniqueOrderedParts'")
      }
      var castValueSeq: Seq[Any] = Seq.empty
      try {
        castValueSeq = values.keys.toSeq.indices
          .map(index => {
            uniqueOrderedParts(index).castFunc.apply(values.get(values.keys.toSeq(index)))
          })
      }
      catch {
        case e: Exception =>
          logger.error(s"Passed values '$values' are not of right type as in specified parts '$uniqueOrderedParts'", e)
          throw e
      }
      castValueSeq.map(value => value.toString).mkString(partDelimiter)
    }

    /**
     * Parse the single parts from a filename
     */
    def parse(name: String): TypeTaggedMap = {
      val split: Seq[String] = name.split(partDelimiter).toSeq
      if (split.length != uniqueOrderedParts.length) {
        throw new IllegalArgumentException(s"Number of values in name '$split' does not match the number of needed parts '$uniqueOrderedParts'")
      }
      val resultMap: Map[Parts, String] = uniqueOrderedParts.zip(split).toMap
      TypedMapStore(mutable.Map.newBuilder(resultMap).result())
    }

  }

  case object ClaimFileNameFormat extends FileNameFormat(Seq(TOPIC, BATCH_NR, CREATION_TIME_IN_MILLIS, NODE_HASH), "_")

}
