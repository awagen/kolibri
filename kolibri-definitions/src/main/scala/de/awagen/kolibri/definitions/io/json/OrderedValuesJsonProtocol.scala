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


package de.awagen.kolibri.definitions.io.json

import de.awagen.kolibri.definitions.utils.OrderedValuesUtils.{folderToFilenamesOrderedValues, fromCsvFileByColumnNames, fromJsonFileMappingToOrderedValues, loadLinesFromFile, mappingsFromCsvFile, paramNameToFileMappingToOrderedValues, paramNameToValuesMappingToOrderedValues}
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues, RangeValues}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}

object OrderedValuesJsonProtocol extends DefaultJsonProtocol {

  val TYPE_KEY = "type"
  val FROM_FILES_LINES_TYPE = "FROM_FILES_LINES_TYPE"
  val VALUES_KEY = "values"
  val FROM_VALUES_TYPE = "FROM_VALUES_TYPE"
  val FROM_RANGE_TYPE = "FROM_RANGE_TYPE"
  val FROM_FILENAME_KEYS_TYPE = "FROM_FILENAME_KEYS_TYPE"
  val DIRECTORY_KEY = "directory"
  val FILES_SUFFIX_KEY = "filesSuffix"
  val FROM_CSV_FILE_TYPE = "FROM_CSV_FILE_TYPE"
  val FILE_KEY = "file"
  val NAME_KEY = "name"
  val COLUMN_SEPARATOR_KEY = "columnSeparator"
  val KEY_COLUMN_KEY = "keyColumn"
  val VALUE_COLUMN_KEY = "valueColumn"
  val FROM_JSON_FILE_MAPPING_TYPE = "FROM_JSON_FILE_MAPPING_TYPE"
  val FROM_CSV_WITH_KEY_AND_VALUE_NAMES_FROM_HEADERS_TYPE = "FROM_CSV_WITH_HEADER_NAMES_TYPE"
  val KEY_NAME_KEY = "keyName"
  val START_VALUE_KEY = "start"
  val END_VALUE_KEY = "end"
  val STEP_SIZE_KEY = "stepSize"

  object OrderedValuesStringFormatStruct extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              FROM_FILENAME_KEYS_TYPE,
              FROM_FILES_LINES_TYPE,
              FROM_VALUES_TYPE,
              FROM_RANGE_TYPE
            )),
            required = true),
          FieldDef(StringConstantStructDef(NAME_KEY), StringStructDef, required = true)
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              FROM_FILENAME_KEYS_TYPE -> Seq(
                FieldDef(StringConstantStructDef(DIRECTORY_KEY), StringStructDef, required = true),
                FieldDef(StringConstantStructDef(FILES_SUFFIX_KEY), StringStructDef, required = true)
              ),
              FROM_FILES_LINES_TYPE -> Seq(
                FieldDef(StringConstantStructDef(FILE_KEY), StringStructDef, required = true)
              ),
              FROM_VALUES_TYPE -> Seq(
                FieldDef(StringConstantStructDef(VALUES_KEY), StringSeqStructDef, required = true)
              ),
              FROM_RANGE_TYPE -> Seq(
                FieldDef(StringConstantStructDef(START_VALUE_KEY), DoubleStructDef, required = true),
                FieldDef(StringConstantStructDef(END_VALUE_KEY), DoubleStructDef, required = true),
                FieldDef(StringConstantStructDef(STEP_SIZE_KEY), DoubleStructDef, required = true),
              )
            )
          )
        )
      )
    }
  }

  object SeqOrderedValuesFormatStruct extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              FROM_FILES_LINES_TYPE,
              FROM_VALUES_TYPE
            )),
            required = true)
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              FROM_FILES_LINES_TYPE -> Seq(
                FieldDef(StringConstantStructDef(VALUES_KEY), MapStructDef(StringStructDef, StringStructDef), required = true)
              ),
              FROM_VALUES_TYPE -> Seq(
                FieldDef(StringConstantStructDef(VALUES_KEY), MapStructDef(StringStructDef, StringSeqStructDef), required = true)
              )
            )
          )
        )
      )
    }
  }

  object OrderedValuesMapFormatStruct extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              FROM_CSV_FILE_TYPE,
              FROM_JSON_FILE_MAPPING_TYPE
            )),
            required = true),
          FieldDef(StringConstantStructDef(NAME_KEY), StringStructDef, required = true)
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              FROM_CSV_FILE_TYPE -> Seq(
                FieldDef(StringConstantStructDef(FILE_KEY), StringStructDef, required = true),
                FieldDef(StringConstantStructDef(COLUMN_SEPARATOR_KEY), StringStructDef, required = true),
                FieldDef(StringConstantStructDef(KEY_COLUMN_KEY), IntStructDef, required = true),
                FieldDef(StringConstantStructDef(VALUE_COLUMN_KEY), IntStructDef, required = true),
              ),
              FROM_JSON_FILE_MAPPING_TYPE -> Seq(
                FieldDef(StringConstantStructDef(FILE_KEY), StringStructDef, required = true)
              )
            )
          )
        )
      )
    }
  }

  object OrderedValuesMultiMapFormatStruct extends WithStructDef {
    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(FROM_CSV_WITH_KEY_AND_VALUE_NAMES_FROM_HEADERS_TYPE)),
            required = true),
          FieldDef(StringConstantStructDef(NAME_KEY), StringStructDef, required = true)
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              FROM_CSV_WITH_KEY_AND_VALUE_NAMES_FROM_HEADERS_TYPE -> Seq(
                FieldDef(StringConstantStructDef(FILE_KEY), StringStructDef, required = true),
                FieldDef(StringConstantStructDef(COLUMN_SEPARATOR_KEY), StringStructDef, required = true),
                FieldDef(StringConstantStructDef(KEY_NAME_KEY), StringStructDef, required = true)
              )
            )
          )
        )
      )
    }
  }

}

case class OrderedValuesJsonProtocol(reader: Reader[String, Seq[String]],
                                     condToOverviewReader: (String => Boolean) => DataOverviewReader) {

  import OrderedValuesJsonProtocol._

  implicit object OrderedValuesStringFormat extends JsonFormat[OrderedValues[String]] {
    override def read(json: JsValue): OrderedValues[String] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case FROM_FILENAME_KEYS_TYPE =>
          // reading values from filename prefix
          val directory: String = fields(DIRECTORY_KEY).convertTo[String]
          val filesSuffix: String = fields(FILES_SUFFIX_KEY).convertTo[String]
          val valueName = fields(NAME_KEY).convertTo[String]
          folderToFilenamesOrderedValues(
            condToOverviewReader,
            directory,
            filesSuffix,
            valueName
          )
        case FROM_FILES_LINES_TYPE =>
          val file: String = fields(FILE_KEY).convertTo[String]
          val valueName = fields(NAME_KEY).convertTo[String]
          loadLinesFromFile(reader, file, valueName)
        case FROM_VALUES_TYPE =>
          val name = fields(NAME_KEY).convertTo[String]
          val values = fields(VALUES_KEY).convertTo[Seq[String]]
          DistinctValues(name, values)
        case FROM_RANGE_TYPE =>
          val name = fields(NAME_KEY).convertTo[String]
          val start = fields(START_VALUE_KEY).convertTo[Double]
          val end = fields(END_VALUE_KEY).convertTo[Double]
          val stepSize = fields(STEP_SIZE_KEY).convertTo[Double]
          // TODO: change this to avoid having to generate the full range
          // right now its just a quick workaround
          DistinctValues(name, RangeValues(name, start, end, stepSize).getAll.map(x => String.format("%.4f", x)))
      }
    }

    override def write(obj: OrderedValues[String]): JsValue = """{}""".toJson

  }

  implicit object SeqOrderedValuesFormat extends JsonFormat[Seq[OrderedValues[String]]] {
    override def read(json: JsValue): Seq[OrderedValues[String]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case FROM_FILES_LINES_TYPE =>
          val paramNameToFile = fields(VALUES_KEY).convertTo[Map[String, String]]
          paramNameToFileMappingToOrderedValues(
            reader,
            paramNameToFile)
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
          val valueName = fields(NAME_KEY).convertTo[String]
          mappingsFromCsvFile(
            reader,
            file,
            columnSeparator,
            keyColumn,
            valueColumn,
            valueName
          )
        case FROM_JSON_FILE_MAPPING_TYPE =>
          // reading mappings from json file
          val file: String = fields(FILE_KEY).convertTo[String]
          val valueName = fields(NAME_KEY).convertTo[String]
          fromJsonFileMappingToOrderedValues(
            reader,
            file,
            valueName
          )
      }
    }

    override def write(obj: OrderedValues[Map[String, String]]): JsValue = """{}""".toJson

  }

  implicit object OrderedValuesMultiMapFormat extends JsonFormat[OrderedValues[Map[String, Seq[String]]]] {
    override def read(json: JsValue): OrderedValues[Map[String, Seq[String]]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case FROM_CSV_WITH_KEY_AND_VALUE_NAMES_FROM_HEADERS_TYPE =>
          val file: String = fields(FILE_KEY).convertTo[String]
          val columnSeparator: String = fields(COLUMN_SEPARATOR_KEY).convertTo[String]
          val keyName: String = fields(KEY_NAME_KEY).convertTo[String]
          val valueName: String = fields(NAME_KEY).convertTo[String]
          fromCsvFileByColumnNames(
            reader,
            file,
            columnSeparator,
            keyName,
            valueName
          )
      }
    }

    override def write(obj: OrderedValues[Map[String, Seq[String]]]): JsValue = """{}""".toJson

  }

}
