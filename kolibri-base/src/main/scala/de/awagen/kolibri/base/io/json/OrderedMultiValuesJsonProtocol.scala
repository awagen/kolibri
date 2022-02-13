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


package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues}
import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues}
import org.slf4j.{Logger, LoggerFactory}
import spray.json._

object OrderedMultiValuesJsonProtocol extends DefaultJsonProtocol {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val DIRECTORY_KEY = "directory"
  val DIRECTORY_SEPARATOR = "/"
  val FILES_SUFFIX_KEY = "filesSuffix"

  val FROM_FILES_LINES_TYPE = "FROM_FILES_LINES"
  val FROM_VALUES_TYPE = "FROM_VALUES"
  val FROM_FILENAME_KEYS_TYPE = "FROM_FILENAME_KEYS"
  val FROM_CSV_FILE_TYPE = "FROM_CSV_FILE"
  val FROM_CSV_WITH_KEY_AND_VALUE_NAMES_FROM_HEADERS = "FROM_CSV_WITH_HEADER_NAMES"
  val FROM_JSON_FILE_MAPPING_TYPE = "FROM_JSON_FILE_MAPPING"
  val TYPE_KEY = "type"
  val VALUES_KEY = "values"
  val VALUE_NAME = "valueName"
  val FILE_KEY = "file"

  val COLUMN_SEPARATOR_KEY = "columnSeparator"
  val KEY_COLUMN_KEY = "keyColumn"
  val VALUE_COLUMN_KEY = "valueColumn"

  implicit object OrderedMultiValuesAnyFormat extends JsonFormat[OrderedMultiValues] {
    override def read(json: JsValue): OrderedMultiValues = {
      try {
        de.awagen.kolibri.datatypes.io.json.OrderedMultiValuesJsonProtocol.OrderedMultiValuesAnyFormat.read(json)
      }
      catch {
        case _: DeserializationException => json match {
          case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
            case FROM_FILES_LINES_TYPE =>
              // passing a parameter name to file mapping, for each parameter extract values from file (one value per
              // line) and return data for all mappings
              val paramNameToFile = fields(VALUES_KEY).convertTo[Map[String, String]]
              var params: Seq[OrderedValues[String]] = Seq.empty
              val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
              paramNameToFile.foreach(x => {
                val name: String = x._1
                val values: Seq[String] = fileReader.read(x._2).map(x => x.trim).filter(x => x.nonEmpty)
                logger.debug(s"found ${values.size} values for param $name, values: $values")
                params = params :+ DistinctValues[String](name, values)
              })
              logger.debug(s"params size=${params.size}, values=$params")
              GridOrderedMultiValues(params)
            case FROM_VALUES_TYPE =>
              // values by passing name to values mappings
              val paramNameToValues = fields(VALUES_KEY).convertTo[Map[String, Seq[String]]]
              var params: Seq[OrderedValues[String]] = Seq.empty
              paramNameToValues.foreach(x => {
                val name: String = x._1
                val values: Seq[String] = x._2.map(x => x.trim).filter(x => x.nonEmpty)
                logger.debug(s"found ${values.size} values for param $name, values: $values")
                params = params :+ DistinctValues[String](name, values)
              })
              logger.debug(s"params size=${params.size}, values=$params")
              GridOrderedMultiValues(params)
            // this would create a generator over the names in the passed folder with given suffix
            // not the full path but just the filename. It would not not extract any values from the files though
            case FROM_FILENAME_KEYS_TYPE =>
              // reading values from filename prefix
              val directory: String = fields(DIRECTORY_KEY).convertTo[String]
              val filesSuffix: String = fields(FILES_SUFFIX_KEY).convertTo[String]
              val valueName = fields(VALUE_NAME).convertTo[String]
              val directoryReader = AppConfig.persistenceModule.persistenceDIModule.dataOverviewReader(x => x.endsWith(filesSuffix))
              val keys: Seq[String] = directoryReader.listResources(directory, _ => true)
                .map(file => file.split(DIRECTORY_SEPARATOR).last.stripSuffix(filesSuffix))
              GridOrderedMultiValues(Seq(DistinctValues[String](valueName, keys)))
            case FROM_CSV_FILE_TYPE =>
              // reading mappings from csv file
              val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
              val file: String = fields(FILE_KEY).convertTo[String]
              val columnSeparator: String = fields(COLUMN_SEPARATOR_KEY).convertTo[String]
              val keyColumn: Int = fields(KEY_COLUMN_KEY).convertTo[Int]
              val valueColumn: Int = fields(VALUE_COLUMN_KEY).convertTo[Int]
              val valueName = fields(VALUE_NAME).convertTo[String]
              val mappings: Map[String, String] = fileReader.read(file)
                .map(x => x.trim.split(columnSeparator))
                .filter(x => x.length >= math.max(keyColumn, valueColumn))
                .map(x => (x(valueColumn), x(keyColumn)))
                .toMap
              GridOrderedMultiValues(Seq(DistinctValues[Map[String, String]](valueName, Seq(mappings))))
            case FROM_JSON_FILE_MAPPING_TYPE =>
              // reading mappings from json file
              val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
              val file: String = fields(FILE_KEY).convertTo[String]
              val valueName = fields(VALUE_NAME).convertTo[String]
              val jsValue: JsValue = fileReader.getSource(file).mkString("\n").parseJson
              val mapping: Map[String, String] = jsValue.convertTo[Map[String, JsValue]].view.mapValues(x => x.toString()).toMap
              GridOrderedMultiValues(Seq(DistinctValues[Map[String, String]](valueName, Seq(mapping))))
          }
        }
      }
    }

    override def write(obj: OrderedMultiValues): JsValue = """{}""".toJson
  }

}
