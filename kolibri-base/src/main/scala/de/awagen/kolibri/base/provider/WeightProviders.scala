/**
 * Copyright 2021 Andreas Wagenmann
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


package de.awagen.kolibri.base.provider

import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.storage.io.reader.Reader
import org.slf4j.{Logger, LoggerFactory}

object WeightProviders {

  private val logger: Logger = LoggerFactory.getLogger(WeightProviders.getClass)

  trait WeightProvider[A] extends (A => Double) with KolibriSerializable

  case class ConstantWeightProvider(value: Double) extends WeightProvider[String] {

    override def apply(v1: String): Double = value

  }

  case class FileBasedStringIdentifierWeightProvider(fileReader: Reader[String, Seq[String]],
                                                     filePath: String,
                                                     identifierMapper: String => String,
                                                     columnDelimiter: String,
                                                     keyColumn: Int,
                                                     weightColumn: Int,
                                                     defaultValue: Double) extends WeightProvider[String] {
    val dataLines: Seq[String] = fileReader.read(filePath)
    logger.debug(s"dataLines: $dataLines")
    val mapping: Map[String, Double] = dataLines.map(x => x.split(columnDelimiter))
      .filter(x => x.length > math.max(keyColumn, weightColumn))
      .map(x => (x(keyColumn), x(weightColumn).toDouble))
      .toMap
    logger.debug(s"mappings: ${mapping}")

    override def apply(v1: String): Double = {
      val useIdentifier: String = identifierMapper.apply(v1)
      logger.debug(s"retrieving weight for original value '$v1', identifier '$useIdentifier'")
      mapping.getOrElse(useIdentifier, defaultValue)
    }
  }

}
