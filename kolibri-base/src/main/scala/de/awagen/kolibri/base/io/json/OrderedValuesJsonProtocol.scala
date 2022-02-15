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

import de.awagen.kolibri.base.utils.OrderedValuesUtils.{folderToFilenamesOrderedValues, fromCsvFileByColumnNames, fromJsonFileMappingToOrderedValues, mappingsFromCsvFile, paramNameToFileMappingToOrderedValues, paramNameToValuesMappingToOrderedValues}
import de.awagen.kolibri.datatypes.values.OrderedValues
import spray.json._


object OrderedValuesJsonProtocol extends DefaultJsonProtocol {

  val TYPE_KEY = "type"
  val FROM_FILES_LINES_TYPE = "FROM_FILES_LINES"
  val VALUES_KEY = "values"
  val FROM_VALUES_TYPE = "FROM_VALUES"
  val FROM_FILENAME_KEYS_TYPE = "FROM_FILENAME_KEYS"
  val DIRECTORY_KEY = "directory"
  val FILES_SUFFIX_KEY = "filesSuffix"
  val VALUE_NAME = "valueName"
  val FROM_CSV_FILE_TYPE = "FROM_CSV_FILE"
  val FILE_KEY = "file"
  val COLUMN_SEPARATOR_KEY = "columnSeparator"
  val KEY_COLUMN_KEY = "keyColumn"
  val VALUE_COLUMN_KEY = "valueColumn"
  val FROM_JSON_FILE_MAPPING_TYPE = "FROM_JSON_FILE_MAPPING"
  val FROM_CSV_WITH_KEY_AND_VALUE_NAMES_FROM_HEADERS = "FROM_CSV_WITH_HEADER_NAMES"
  val KEY_NAME_KEY = "keyName"


  implicit object OrderedValuesStringFormat extends JsonFormat[OrderedValues[String]] {
    override def read(json: JsValue): OrderedValues[String] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case FROM_FILENAME_KEYS_TYPE =>
          // reading values from filename prefix
          val directory: String = fields(DIRECTORY_KEY).convertTo[String]
          val filesSuffix: String = fields(FILES_SUFFIX_KEY).convertTo[String]
          val valueName = fields(VALUE_NAME).convertTo[String]
          folderToFilenamesOrderedValues(directory, filesSuffix, valueName)
      }
    }

    override def write(obj: OrderedValues[String]): JsValue = """{}""".toJson
  }

  implicit object SeqOrderedValuesFormat extends JsonFormat[Seq[OrderedValues[String]]] {
    override def read(json: JsValue): Seq[OrderedValues[String]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case FROM_FILES_LINES_TYPE =>
          val paramNameToFile = fields(VALUES_KEY).convertTo[Map[String, String]]
          paramNameToFileMappingToOrderedValues(paramNameToFile)
        case FROM_VALUES_TYPE =>
          // values by passing name to values mappings
          val paramNameToValues = fields(VALUES_KEY).convertTo[Map[String, Seq[String]]]
          paramNameToValuesMappingToOrderedValues(paramNameToValues)
      }
    }

    override def write(obj: Seq[OrderedValues[String]]): JsValue = """{}""".toJson
  }

  implicit object OrderedValuesMapFormat extends JsonFormat[OrderedValues[Map[String, String]]] {
    override def read(json: JsValue): OrderedValues[Map[String, String]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case FROM_CSV_FILE_TYPE =>
          // reading mappings from csv file
          val file: String = fields(FILE_KEY).convertTo[String]
          val columnSeparator: String = fields(COLUMN_SEPARATOR_KEY).convertTo[String]
          val keyColumn: Int = fields(KEY_COLUMN_KEY).convertTo[Int]
          val valueColumn: Int = fields(VALUE_COLUMN_KEY).convertTo[Int]
          val valueName = fields(VALUE_NAME).convertTo[String]
          mappingsFromCsvFile(file, columnSeparator, keyColumn, valueColumn, valueName)
        case FROM_JSON_FILE_MAPPING_TYPE =>
          // reading mappings from json file
          val file: String = fields(FILE_KEY).convertTo[String]
          val valueName = fields(VALUE_NAME).convertTo[String]
          fromJsonFileMappingToOrderedValues(file, valueName)
      }
    }

    override def write(obj: OrderedValues[Map[String, String]]): JsValue = """{}""".toJson
  }

  implicit object OrderedValuesMultiMapFormat extends JsonFormat[OrderedValues[Map[String, Seq[String]]]] {
    override def read(json: JsValue): OrderedValues[Map[String, Seq[String]]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case FROM_CSV_WITH_KEY_AND_VALUE_NAMES_FROM_HEADERS =>
          val file: String = fields(FILE_KEY).convertTo[String]
          val columnSeparator: String = fields(COLUMN_SEPARATOR_KEY).convertTo[String]
          val keyName: String = fields(KEY_NAME_KEY).convertTo[String]
          val valueName: String = fields(VALUE_NAME).convertTo[String]
          fromCsvFileByColumnNames(file, columnSeparator, keyName, valueName)
      }
    }

    override def write(obj: OrderedValues[Map[String, Seq[String]]]): JsValue = """{}""".toJson
  }

}
