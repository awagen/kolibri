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

import de.awagen.kolibri.base.io.json.OrderedValuesJsonProtocol.{OrderedValuesMapFormat, OrderedValuesStringFormat, SeqOrderedValuesFormat}
import de.awagen.kolibri.base.utils.OrderedValuesUtils.fromCsvFileByColumnNames
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues}
import de.awagen.kolibri.datatypes.values.OrderedValues
import spray.json._

object OrderedMultiValuesJsonProtocol extends DefaultJsonProtocol {

  val FROM_FILES_LINES_TYPE = "FROM_FILES_LINES_TYPE"
  val FROM_VALUES_TYPE = "FROM_VALUES_TYPE"
  val FROM_FILENAME_KEYS_TYPE = "FROM_FILENAME_KEYS_TYPE"
  val FROM_CSV_FILE_TYPE = "FROM_CSV_FILE_TYPE"
  val FROM_CSV_WITH_KEY_AND_VALUE_NAMES_FROM_HEADERS_TYPE = "FROM_CSV_WITH_HEADER_NAMES_TYPE"
  val FROM_JSON_FILE_MAPPING_TYPE = "FROM_JSON_FILE_MAPPING_TYPE"
  val TYPE_KEY = "type"
  val VALUE_NAME = "valueName"
  val FILE_KEY = "file"
  val COLUMN_SEPARATOR_KEY = "columnSeparator"
  val KEY_NAME_KEY = "keyName"

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
              val params: Seq[OrderedValues[String]] = json.convertTo[Seq[OrderedValues[String]]]
              GridOrderedMultiValues(params)
            case FROM_VALUES_TYPE =>
              val params = json.convertTo[Seq[OrderedValues[String]]]
              GridOrderedMultiValues(params)
            // this would create a generator over the names in the passed folder with given suffix
            // not the full path but just the filename. It would not not extract any values from the files though
            case FROM_FILENAME_KEYS_TYPE =>
              val values = json.convertTo[OrderedValues[String]]
              GridOrderedMultiValues(Seq(values))
            case FROM_CSV_FILE_TYPE =>
              val mappings = json.convertTo[OrderedValues[Map[String, String]]]
              GridOrderedMultiValues(Seq(mappings))
            case FROM_JSON_FILE_MAPPING_TYPE =>
              val values = json.convertTo[OrderedValues[Map[String, String]]]
              GridOrderedMultiValues(Seq(values))
            case FROM_CSV_WITH_KEY_AND_VALUE_NAMES_FROM_HEADERS_TYPE =>
              val file: String = fields(FILE_KEY).convertTo[String]
              val columnSeparator: String = fields(COLUMN_SEPARATOR_KEY).convertTo[String]
              val keyName: String = fields(KEY_NAME_KEY).convertTo[String]
              val valueName: String = fields(VALUE_NAME).convertTo[String]
              val values = fromCsvFileByColumnNames(file, columnSeparator, keyName, valueName)
              GridOrderedMultiValues(Seq(values))
          }
        }
      }
    }

    override def write(obj: OrderedMultiValues): JsValue = """{}""".toJson
  }

}
