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


package de.awagen.kolibri.base.utils

import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.base.io.reader.FileReaderUtils
import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues}
import org.slf4j.{Logger, LoggerFactory}
import spray.json._

object OrderedValuesUtils extends DefaultJsonProtocol {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private[this] val DIRECTORY_SEPARATOR = "/"

  def logValues(values: Seq[Any], name: String): Unit = {
    logger.debug(s"found ${values.size} values for param $name, values: $values")
  }

  def loadLinesFromFile(file: String, valuesName: String): OrderedValues[String] = {
    val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
    val values = FileReaderUtils.loadLinesFromFile(file, fileReader)
    DistinctValues(valuesName, values)
  }

  /**
   * Given a mapping of parameter name to filename, read all lines in file as values of the corresponding
   * mapped parameter name. Generates OrderedValues for each parameter and collects in Seq.
   * @param mapping - key: parameter name, value: file to extract the values from (one value per line)
   * @return - Sequence of created OrderedValues
   */
  def paramNameToFileMappingToOrderedValues(mapping: Map[String, String]): Seq[OrderedValues[String]] = {
    val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
    mapping.map(x => {
      val name: String = x._1
      val values: Seq[String] = fileReader.read(x._2).map(x => x.trim).filter(x => x.nonEmpty)
      logValues(values, name)
      DistinctValues[String](name, values)
    }).toSeq
  }

  /**
   * Takes map with key=parameterName, value=Seq of values and for each key creates OrderedValues of it.
   * @param mapping: key: parameterName, value: Seq of values
   * @return Seq containing all OrderedValues corresponding to the key / values mapping passed
   */
  def paramNameToValuesMappingToOrderedValues(mapping: Map[String, Seq[String]]): Seq[OrderedValues[String]] = {
    mapping.map(x => {
      val name: String = x._1
      val values: Seq[String] = x._2.map(x => x.trim).filter(x => x.nonEmpty)
      logValues(values, name)
      DistinctValues[String](name, values)
    }).toSeq
  }

  /**
   * Given a directory and filesSuffix and name of the value, extracts all filenames and strips the suffix,
   * then uses the filenames without the suffix as values for the value named by valueName.
   * @param directory: directory to extract the filenames without suffix
   * @param filesSuffix: the suffix used to filter files and to strip off the filename
   * @param valueName: the name of the parameter for which to use the filtered/prepared filenames as values
   * @return: OrderedValues with name given by valueName and values given by the filenames in given directory matching
   * the suffix (values with stripped off suffix are used in the returned OrderedValues).
   */
  def folderToFilenamesOrderedValues(directory: String, filesSuffix: String, valueName: String): OrderedValues[String] = {
    val directoryReader = AppConfig.persistenceModule.persistenceDIModule.dataOverviewReader(x => x.endsWith(filesSuffix))
    val keys: Seq[String] = directoryReader.listResources(directory, _ => true)
      .map(file => file.split(DIRECTORY_SEPARATOR).last.stripSuffix(filesSuffix))
    DistinctValues[String](valueName, keys)
  }

  /**
   * Given a csv file, columnSeparator, key and value columns, extract the key value mapping per row.
   * Creates OrderedValues off those 1:1 mappings (here its assumed each key has only one value).
   * @param file: csv file
   * @param columnSeparator: column separator used in the file
   * @param keyColumn: 0-based index of the column used to extract the keys
   * @param valueColumn: 0-based index of the column used to extract the values
   * @param valueName: value name used as name in the OrderedValues
   * @return OrderedValues with only single element (can pick head of the corresponding Seq), giving 1:1 mappings
   *         as given in the csv files (in case a key has multiple mappings, would only pick last)
   */
  def mappingsFromCsvFile(file: String,
                          columnSeparator: String,
                          keyColumn: Int,
                          valueColumn: Int,
                          valueName: String): OrderedValues[Map[String, String]] = {
    val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
    val mappings: Map[String, String] = fileReader.read(file)
      .map(x => x.trim.split(columnSeparator))
      .filter(x => x.length >= math.max(keyColumn, valueColumn))
      .map(x => (x(valueColumn), x(keyColumn)))
      .toMap
    DistinctValues[Map[String, String]](valueName, Seq(mappings))
  }

  /**
   * Similar to mappingsFromCsvFile, yet instead of using key and value indices, here names are used and its assumed
   * that the first line of the csv has the column names as columns such that the passed key and value names can be
   * mapped to it. Note that this variant takes key:value mappings of 1:N into account, e.g allows multiple values per key.
   * @param file: csv file
   * @param columnSeparator: column separator used in the csv file
   * @param keyName: name of the key column
   * @param valueName: name of the value column
   * @return
   */
  def fromCsvFileByColumnNames(file: String, columnSeparator: String, keyName: String, valueName: String): OrderedValues[Map[String, Seq[String]]] = {
    val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
    val lines: Seq[String] = fileReader.read(file)
    val headerColumns: Seq[String] = lines.head.split(columnSeparator)
      .map(x => x.trim)
      .toSeq
    val headerIndexMappings: Map[String, Int] = headerColumns.indices
      .map(x => (headerColumns(x), x))
      .toMap
    val keyColumnOpt: Option[Int] = headerIndexMappings.get(keyName)
    val valueColumnOpt: Option[Int] = headerIndexMappings.get(valueName)
    // now extract the values
    val data: Option[Map[String, Seq[String]]] = for {
      keyColumn <- keyColumnOpt
      valueColumn <- valueColumnOpt
    } yield {
      lines.tail
        .map(x => x.split(columnSeparator).map(y => y.trim).toSeq)
        .map(x => (x(keyColumn), x(valueColumn)))
        .foldLeft(Map.empty[String, Seq[String]])((x, y) => x + (y._1 -> (x.getOrElse(y._1, Seq.empty[String]) :+ y._2)))
    }
    DistinctValues[Map[String, Seq[String]]](valueName, Seq(data.get))
  }

  /**
   * Reads mappings from json, assuming 1:1 key-value mappings and a reasonable string representation of the values
   * @param file: file to read data from. Data is assumed to be a json.
   * @param valueName: name of the values to extract
   * @return
   */
  def fromJsonFileMappingToOrderedValues(file: String, valueName: String): DistinctValues[Map[String, String]] = {
    val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
    val jsValue: JsValue = fileReader.getSource(file).mkString("\n").parseJson
    val mapping: Map[String, String] = jsValue.convertTo[Map[String, JsValue]].view.mapValues(x => x.toString()).toMap
    DistinctValues[Map[String, String]](valueName, Seq(mapping))
  }

}
