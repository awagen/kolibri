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

package de.awagen.kolibri.base.io.reader

import scala.collection.immutable
import scala.io.Source

object FileReaderUtils {

  def localResourceSource(filename: String, encoding: String = "UTF-8"): Source = {
    Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(filename), encoding)
  }

  def localSource(fullFilePath: String, encoding: String = "UTF-8"): Source = {
    Source.fromFile(fullFilePath, encoding)
  }

  def trimmedEntriesByLineFromFile(source: Source): immutable.Seq[String] = {
    source
      .getLines
      .filter(f => f.trim.nonEmpty)
      .map(x => x.trim).toVector
  }

  def trimmedEntriesByDelimiterFromFile(source: Source, delimiter: String): immutable.Seq[String] = {
    source
      .getLines.map(x => x.trim)
      .filter(f => f.trim.nonEmpty)
      .flatMap(x => x.split(delimiter))
      .map(x => x.trim)
      .toVector
  }

  /**
    * Read lines from source, trim, filter out empty, split by delimiter, filter out those where nr of elements
    * is smaller than actually selected positions, select element at given position, filter out empty
    *
    * @param source
    * @param delimiter
    * @param position
    * @return
    */
  def pickUniquePositionPerLineDeterminedByDelimiter(source: Source, delimiter: String, position: Int): immutable.Seq[String] = {
    source
      .getLines.map(x => x.trim)
      .filter(f => f.trim.nonEmpty)
      .map(x => x.split(delimiter))
      .filter(x => x.length > position)
      .map(x => x(position).trim)
      .filter(x => x.nonEmpty)
      .distinct
      .toVector
  }


  def mappingFromCSVSource[T](columnDelimiter: String,
                              filterLessColumnsThan: Int,
                              valsToKey: Seq[String] => String,
                              columnsToValue: Array[String] => T): Source => Map[String, T] = {
    source => {
      source.getLines()
        .filter(f => f.trim.nonEmpty && !f.startsWith("#"))
        .map(x => x.split(columnDelimiter))
        .filter(x => x.length >= filterLessColumnsThan)
        .map(x => valsToKey(x) -> columnsToValue(x))
        .toMap
    }
  }


  def multiMappingFromCSVFile[T](source: Source,
                                 columnDelimiter: String,
                                 filterLessColumnsThan: Int,
                                 valsToKey: Seq[String] => String,
                                 columnsToValue: Array[String] => T): Map[String, Set[T]] = {
    val mappings: Seq[(String, T)] = source
      .getLines
      .filter(f => f.trim.nonEmpty && !f.startsWith("#"))
      .map(x => x.split(columnDelimiter))
      .filter(x => x.length == filterLessColumnsThan)
      .map(x => valsToKey(x) -> columnsToValue(x))
      .toSeq
    var productIdMap: Map[String, Set[T]] = Map.empty[String, Set[T]]
    mappings.foreach(kvPair =>
      productIdMap = productIdMap + (kvPair._1 -> (productIdMap.getOrElse(kvPair._1, Set.empty[T]) + kvPair._2)))
    productIdMap
  }


  /**
    * Check if file can be found within the classpath
    *
    * @param filename
    * @return
    */
  def isLocalResourceFileAvailable(filename: String): Boolean = {
    getClass.getClassLoader.getResource(filename) != null
  }

}
