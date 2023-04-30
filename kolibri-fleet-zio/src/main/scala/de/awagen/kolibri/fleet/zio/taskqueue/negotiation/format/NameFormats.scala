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

import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, TypeTaggedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.types.NamedClassTyped
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.Parts.{BATCH_NR, CREATION_TIME_IN_MILLIS, ID, NODE_HASH, Parts, TOPIC}
import org.slf4j.{Logger, LoggerFactory}

import scala.reflect.runtime.universe._


/**
 * Composition of fileNames to simplify file based messaging
 */
object NameFormats {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  object Parts extends Enumeration {
    type Parts = PartsValue[_]

    case class PartsValue[T: TypeTag](namedClassTyped: NamedClassTyped[T], parseFunc: String => T)

    val NODE_HASH: PartsValue[String] = PartsValue[String](NamedClassTyped[String]("nodeHash"), identity)
    val CREATION_TIME_IN_MILLIS: PartsValue[Long] = PartsValue[Long](NamedClassTyped[Long]("creationTimeInMillis"), x => x.toLong)
    val TOPIC: PartsValue[String] = PartsValue[String](NamedClassTyped[String]("topic"), identity)
    val ID: PartsValue[String] = PartsValue[String](NamedClassTyped[String]("id"), identity)
    val BATCH_NR: PartsValue[Int] = PartsValue[Int](NamedClassTyped[Int]("batchNr"), x => x.toInt)
  }

  /**
   * Note that we use a TypeTaggedMap with strong typing for formatting
   * (since keys and related types are known) and for parse we use a
   * WeaklyTypedMap, which allows any mapping but will only return
   * a value for a key if the requested type matches
   */
  trait NameFormat {

    def uniqueOrderedParts: Seq[Parts]

    def partDelimiter: String

    def format(values: TypeTaggedMap): String

    def parse(name: String): WeaklyTypedMap[String]

  }

  object NameFormat {

    def getCreationTimeInMillis(map: TypeTaggedMap): Option[Long] = map.get(CREATION_TIME_IN_MILLIS.namedClassTyped)
    def getTopic(map: TypeTaggedMap): Option[String] = map.get(TOPIC.namedClassTyped)
    def getId(map: TypeTaggedMap): Option[String] = map.get(ID.namedClassTyped)
    def getBatchNr(map: TypeTaggedMap): Option[Int] = map.get(BATCH_NR.namedClassTyped)
    def getNodeHash(map: TypeTaggedMap): Option[String] = map.get(NODE_HASH.namedClassTyped)

  }

  /**
   * Specify the used ordering of the added parts.
   */
  class FileNameFormat(orderedParts: Seq[Parts], val partDelimiter: String = "_") extends NameFormat {

    val uniqueOrderedParts: Seq[Parts] = orderedParts.distinct

    /**
     * Checks whether each value corresponds to a specified part in orderedParts, and tests whether
     * casting to the needed type would work.
     * If so, construct the string value.
     */
    def format(values: TypeTaggedMap): String = {
      if (uniqueOrderedParts.exists(part => !values.keys.toSeq.contains(part.namedClassTyped))) {
        throw new IllegalArgumentException(s"Values in '$values' do not contain all needed parts '$uniqueOrderedParts'")
      }
      var castValueSeq: Seq[_] = Seq.empty
      try {
        castValueSeq = values.keys.toSeq.indices
          .map(index => {
            values.get(uniqueOrderedParts(index).namedClassTyped).get
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
    def parse(name: String): WeaklyTypedMap[String] = {
      val split: Seq[String] = name.split(partDelimiter).toSeq
      if (split.length != uniqueOrderedParts.length) {
        throw new IllegalArgumentException(s"Number of values in name '$split' does not match the number of needed parts '$uniqueOrderedParts'")
      }
      val mapStore = BaseWeaklyTypedMap.empty
      uniqueOrderedParts.zip(split)
        .foreach(x => {
          val parsedValue = x._1.parseFunc.apply(x._2)
          mapStore.put(x._1.namedClassTyped.name, parsedValue)
        })
      mapStore
    }

  }

}
