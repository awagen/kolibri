/**
 * Copyright 2022 Andreas Wagenmann
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

import de.awagen.kolibri.base.directives.ResourceDirectives
import de.awagen.kolibri.base.directives.ResourceDirectives._
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import spray.json.DefaultJsonProtocol.{IntJsonFormat, StringJsonFormat, mapFormat}
import spray.json.{JsValue, JsonFormat, enrichAny}

object ResourceDirectiveJsonProtocol {

  val TYPE_KEY = "type"
  val FILE_KEY = "file"
  val FOLDER_KEY = "folder"
  val FILE_SUFFIX_KEY = "file_suffix"
  val COLUMN_DELIMITER_KEY = "column_delimiter"
  val KEY_COLUMN_INDEX_KEY = "keyColumnIndex"
  val VALUE_COLUMN_INDEX_KEY = "valueColumnIndex"
  val KEY_TO_VALUE_FILE_MAP_KEY = "keyToValueFileMap"

  implicit object MapStringDoubleResourceDirectiveFormat extends JsonFormat[ResourceDirective[Map[String, Double]]] with WithStructDef {
    val JUDGEMENTS_FROM_FILE_TYPE = "JUDGEMENTS_FROM_FILE"

    override def read(json: JsValue): ResourceDirective[Map[String, Double]] = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case JUDGEMENTS_FROM_FILE_TYPE =>
          val file: String = fields(FILE_KEY).convertTo[String]
          ResourceDirectives.getGetJudgementsResourceDirectiveByFile(file)
      }
    }

    // TODO
    override def write(obj: ResourceDirective[Map[String, Double]]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(TYPE_KEY),
          StringChoiceStructDef(Seq(JUDGEMENTS_FROM_FILE_TYPE)),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(FILE_KEY),
          RegexStructDef("[a-zA-Z]\\w*".r),
          required = true
        )
      ),
      Seq.empty
    )
  }

  implicit object MapStringGeneratorStringResourceDirectiveFormat extends JsonFormat[ResourceDirective[Map[String, IndexedGenerator[String]]]] with WithStructDef {
    val MAPPING_FROM_FOLDER_WITH_KEY_FROM_FILE_NAME_AND_VALUES_FROM_CONTENT_TYPE = "MAPPING_FROM_FOLDER_WITH_KEY_FROM_FILE_NAME_AND_VALUES_FROM_CONTENT"
    val MAPPING_FROM_CSV_FILE_TYPE = "MAPPING_FROM_CSV_FILE"
    val MAPPINGS_FROM_ARRAY_VALUE_JSON_TYPE = "MAPPINGS_FROM_ARRAY_VALUE_JSON"
    val MAPPINGS_FROM_SINGLE_VALUE_JSON_TYPE = "MAPPINGS_FROM_SINGLE_VALUE_JSON"
    val MAPPINGS_FROM_KEY_FILE_MAPPING_TYPE = "MAPPINGS_FROM_KEY_FILE_MAPPING"

    override def read(json: JsValue): ResourceDirective[Map[String, IndexedGenerator[String]]] = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case MAPPING_FROM_FOLDER_WITH_KEY_FROM_FILE_NAME_AND_VALUES_FROM_CONTENT_TYPE =>
          val folder: String = fields(FOLDER_KEY).convertTo[String]
          val fileSuffix: String = fields(FILE_SUFFIX_KEY).convertTo[String]
          getMappingsFromFolderWithKeyFromFileNameAndValuesFromContent(folder, fileSuffix)
        case MAPPING_FROM_CSV_FILE_TYPE =>
          val file: String = fields(FILE_KEY).convertTo[String]
          val columnDelimiter: String = fields(COLUMN_DELIMITER_KEY).convertTo[String]
          val keyColumnIndex: Int = fields(KEY_COLUMN_INDEX_KEY).convertTo[Int]
          val valueColumnIndex: Int = fields(VALUE_COLUMN_INDEX_KEY).convertTo[Int]
          getMappingsFromCsvFile(file, columnDelimiter, keyColumnIndex, valueColumnIndex)
        case MAPPINGS_FROM_ARRAY_VALUE_JSON_TYPE =>
          val file: String = fields(FILE_KEY).convertTo[String]
          getJsonArrayMappingsFromFile(file)
        case MAPPINGS_FROM_SINGLE_VALUE_JSON_TYPE =>
          val file: String = fields(FILE_KEY).convertTo[String]
          getJsonSingleMappingsFromFile(file)
        case MAPPINGS_FROM_KEY_FILE_MAPPING_TYPE =>
          val keyToValueMap: Map[String, String] = fields(KEY_TO_VALUE_FILE_MAP_KEY).convertTo[Map[String, String]]
          getMappingFromKeyFileMappings(keyToValueMap)
      }
    }

    // TODO
    override def write(obj: ResourceDirective[Map[String, IndexedGenerator[String]]]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(TYPE_KEY),
          StringChoiceStructDef(Seq(
            MAPPING_FROM_FOLDER_WITH_KEY_FROM_FILE_NAME_AND_VALUES_FROM_CONTENT_TYPE,
            MAPPING_FROM_CSV_FILE_TYPE,
            MAPPINGS_FROM_ARRAY_VALUE_JSON_TYPE,
            MAPPINGS_FROM_SINGLE_VALUE_JSON_TYPE,
            MAPPINGS_FROM_KEY_FILE_MAPPING_TYPE
          )),
          required = true
        ),
      ),
      Seq(
        ConditionalFields(
          TYPE_KEY,
          Map(
            MAPPING_FROM_FOLDER_WITH_KEY_FROM_FILE_NAME_AND_VALUES_FROM_CONTENT_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(FOLDER_KEY),
                RegexStructDef("[a-zA-Z]\\w*".r),
                required = true
              ),
              FieldDef(
                StringConstantStructDef(FILE_SUFFIX_KEY),
                RegexStructDef(".*".r),
                required = true
              )
            ),
            MAPPING_FROM_CSV_FILE_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(FILE_KEY),
                RegexStructDef("[a-zA-Z]\\w*".r),
                required = true
              ),
              FieldDef(
                StringConstantStructDef(COLUMN_DELIMITER_KEY),
                RegexStructDef(".+".r),
                required = true
              ),
              FieldDef(
                StringConstantStructDef(KEY_COLUMN_INDEX_KEY),
                IntMinMaxStructDef(0, 1000),
                required = true
              ),
              FieldDef(
                StringConstantStructDef(VALUE_COLUMN_INDEX_KEY),
                IntMinMaxStructDef(0, 1000),
                required = true
              )
            ),
            MAPPINGS_FROM_ARRAY_VALUE_JSON_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(FILE_KEY),
                RegexStructDef("[a-zA-Z]\\w*".r),
                required = true
              )
            ),
            MAPPINGS_FROM_SINGLE_VALUE_JSON_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(FILE_KEY),
                RegexStructDef("[a-zA-Z]\\w*".r),
                required = true
              )
            ),
            MAPPINGS_FROM_KEY_FILE_MAPPING_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(KEY_TO_VALUE_FILE_MAP_KEY),
                MapStructDef(StringStructDef, StringStructDef),
                required = true
              )
            )
          )
        )
      )
    )
  }

}
