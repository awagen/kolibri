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

import de.awagen.kolibri.base.config.AppConfig
import org.slf4j.{Logger, LoggerFactory}
import spray.json._

import scala.collection.{immutable, mutable}
import scala.io.Source

object FileReaderUtils extends DefaultJsonProtocol {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  object JsValueOps {
    val JS_ARRAY_SIZE: JsValue => Int = x => x.asInstanceOf[JsArray].elements.size

    val JS_OBJECT_KEY_SIZE: JsValue => Int = x => x.asInstanceOf[JsObject].fields.size

    val JS_OBJECT_MAPPED_VALUES_SIZE: JsValue => Int = x => x.asInstanceOf[JsObject].fields
      .map(x => x._2.asInstanceOf[JsArray].elements.size).sum

    def jsArraySamples(numSamples: Int): JsValue => JsArray = x => JsArray(
      x.asInstanceOf[JsArray]
        .elements
        .take(numSamples))

    def jsObjectMappingSamples(numSamples: Int): JsValue => JsArray = x => {
      val samples: immutable.Iterable[JsObject] = x.asJsObject
        .fields
        .map(x => (x._1, x._2.convertTo[Seq[JsValue]]))
        .flatMap(x => x._2.map(y => {
          JsObject(x._1 -> y)
        }))
        .take(numSamples).toSeq
      new JsArray(samples.toVector)
    }
  }

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

  /**
   * Given some string, split by lines and look at those lines starting with '#' for any key value mapping,
   * extract
   *
   * @param content
   * @return
   */
  def extractMetaData(content: String): Map[String, String] = {
    val metaData: mutable.Map[String, String] = mutable.Map.empty
    content.split("\n").map(x => x.trim)
      .filter(x => x.nonEmpty)
      .foreach({
        case e if e.startsWith("#") && e.contains("=") =>
          val split = e.split("=").map(x => x.trim)
          if (split.length == 2) {
            metaData(split.head.stripPrefix("#")) = split.last
          }
        case _ =>
      })
    metaData.toMap
  }

  /**
   * Extract lines from string that are actual content (e.g not prefixed by #), concatenates those
   * and parses as json (input string must be in json format)
   *
   * @return
   */
  def parseJsonContent: String => JsValue = x => {
    val dataLines: Seq[String] = extractContentLines(x)
    dataLines.mkString("\n").parseJson
  }

  /**
   * Extracting actual content lines from string. Filters out empty lines and lines either used for
   * comments or meta data (starting with '#')
   *
   * @param content
   * @return
   */
  def extractContentLines(content: String): Seq[String] = {
    content.split("\n")
      .map(x => x.trim)
      .filter(x => x.nonEmpty)
      .filter(x => !x.startsWith("#"))
  }

  /**
   * transforming a string with one value per line (separated by '\n') into JsValue of type JsArray
   * containing all non-empty lines
   *
   * @param lineCastFunc
   * @return
   */
  def stringToValueSeqFunc(lineCastFunc: String => JsValue): String => JsValue = x => {
    JsArray(
      x.split("\n")
        .map(x => x.trim)
        .filter(x => !x.startsWith("#") && x.nonEmpty)
        .map(x => lineCastFunc.apply(x))
        .toSeq: _*
    )
  }

  /**
   * Given string in csv format, extract a mapping Map[String, Seq[T]] where
   * column1 holds the key values, column2 holds the value for the key,
   * and in case a key occurs multiple times just keeps all values in a value Seq,
   * thus the actual json returned will reflect a Map[String, Seq[T]]
   *
   * @return
   */
  def csvToMapping[T](columnsToValueFunc: Array[String] => T)(implicit writer: JsonWriter[Map[String, Set[T]]]): String => JsValue = x => {
    val metaData: Map[String, String] = extractMetaData(x)
    val delimiter = metaData.getOrElse("delimiter", ",")
    multiMappingFromCSVFile[T](
      source = Source.fromString(x),
      columnDelimiter = delimiter,
      filterLessColumnsThan = 2,
      valsToKey = x => x.head,
      columnsToValue = columnsToValueFunc
    ).toJson
  }

  /**
   * From a passed source in csv format extract key to (multiple) values mapping.
   * In case a key only occurs once, the value Seq mapped to it will only contain this
   * one element, otherwise all values mapped against it in the csv.
   *
   * @param source                - actual content source
   * @param columnDelimiter       - delimiter to split columns by
   * @param filterLessColumnsThan - filter out rows that have column count less than this value
   * @param valsToKey             - function to transform a row (Seq of columns) into the key value
   * @param columnsToValue        - function to transform a row (Seq of columns) into a mapped value
   * @tparam T - type a value is mapped to
   * @return mapping of key values from the key column to its mapped values
   */
  def multiMappingFromCSVFile[T](source: Source,
                                 columnDelimiter: String,
                                 filterLessColumnsThan: Int,
                                 valsToKey: Seq[String] => String,
                                 columnsToValue: Array[String] => T): Map[String, Set[T]] = {
    val mappings: Seq[(String, T)] = source
      .getLines
      .map(x => x.trim)
      .filter(f => f.nonEmpty && !f.startsWith("#"))
      .map(x => x.split(columnDelimiter))
      .filter(x => x.length >= filterLessColumnsThan)
      .map(x => valsToKey(x) -> columnsToValue(x))
      .toSeq
    var map: Map[String, Set[T]] = Map.empty[String, Set[T]]
    mappings.foreach(kvPair =>
      map = map + (kvPair._1 -> (map.getOrElse(kvPair._1, Set.empty[T]) + kvPair._2)))
    map
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

  /**
   * Passing file and reader, provides the lines contained in the file
   *
   * @param file
   * @param reader
   * @return
   */
  def loadLinesFromFile(file: String, reader: Reader[String, Seq[String]]): Seq[String] = {
    reader.read(file)
      .map(x => x.trim)
      .filter(x => x.nonEmpty)
      .filter(x => !x.startsWith("#"))
  }

  /**
   * read mapping from json file
   *
   * @param file - file containing the json mapping
   * @return
   */
  def readJsonMapping[T](file: String, reader: Reader[String, Seq[String]], valueConversion: JsValue => T): Map[String, T] = {
    val jsonContent = reader.getSource(file).getLines().mkString("\n")
    val jsValue: JsValue = jsonContent.parseJson
    jsValue.convertTo[Map[String, JsValue]].map(x => (x._1, valueConversion.apply(x._2)))
  }

  /**
   * Search in directory for files with given fileSuffix, then remove directory path and the suffix to retrieve
   * the file identifier, and extract per-line values from file and map the values to its file identifier
   *
   * @param directory   - directory to identify valid files as per passed suffix
   * @param filesSuffix - suffix used to filter files
   * @return
   */
  def extractFilePrefixToLineValuesMapping(directory: String, filesSuffix: String, directorySeparator: String = "/"): Map[String, Seq[String]] = {
    val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
    val directoryReader = AppConfig.persistenceModule.persistenceDIModule.dataOverviewReader(x => x.endsWith(filesSuffix))
    directoryReader.listResources(directory, _ => true)
      .map(file => {
        val fileIdentifier = file.split(directorySeparator).last.stripSuffix(filesSuffix)
        val values = fileReader.getSource(file).getLines()
          .map(l => l.trim)
          .filter(l => l.nonEmpty)
          .filter(l => !l.startsWith("#"))
          .toSeq
        (fileIdentifier, values)
      }).toMap
  }

}
