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
import de.awagen.kolibri.base.io.json.EnumerationJsonProtocol._
import de.awagen.kolibri.base.io.json.OrderedValuesJsonProtocol._
import de.awagen.kolibri.base.io.reader.FileReaderUtils
import de.awagen.kolibri.base.processing.modifiers.ParameterValues._
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{ConditionalFields, GenericSeqStructDef, IntMinMaxStructDef, IntSeqStructDef, IntStructDef, MapStructDef, NestedFieldSeqStructDef, StringChoiceStructDef, StringConstantStructDef, StringSeqStructDef, StringStructDef}
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.datatypes.values.OrderedValues
import org.slf4j.LoggerFactory
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat, enrichAny}

object ParameterValuesJsonProtocol extends DefaultJsonProtocol {

  private[this] val logger = LoggerFactory.getLogger(this.getClass)

  object FormatOps {

    def valuesFromLinesJson(valuesType: ValueType.Value, paramIdentifier: String, filePath: String): String = {
      s"""
         |{
         |"$TYPE_KEY": "$FROM_ORDERED_VALUES_TYPE",
         |"$VALUES_TYPE_KEY": "${valuesType.toString}",
         |"$VALUES_KEY": {
         |  "$TYPE_KEY": "$FROM_FILES_LINES_TYPE",
         |  "$NAME_KEY": "$paramIdentifier",
         |  "$FILE_KEY": "$filePath"
         |}
         |}
         |""".stripMargin
    }

    def valuesFromCsvMapping(valuesType: ValueType.Value, paramIdentifier: String, filePath: String,
                             delimiter: String): String = {
      s"""{
         |"$TYPE_KEY": "$CSV_MAPPINGS_TYPE",
         |"$NAME_KEY": "$paramIdentifier",
         |"$VALUES_TYPE_KEY": "${valuesType.toString}",
         |"$VALUES_KEY": "$filePath",
         |"$COLUMN_DELIMITER_KEY": "$delimiter",
         |"$KEY_COLUMN_INDEX_KEY": 0,
         |"$VALUE_COLUMN_INDEX_KEY": 1
         |}
         |""".stripMargin
    }

    def valuesFromJsonMapping(valuesType: ValueType.Value, paramIdentifier: String, filePath: String): String = {
      s"""{
         |"$TYPE_KEY": "$JSON_ARRAY_MAPPINGS_TYPE",
         |"$NAME_KEY": "$paramIdentifier",
         |"$VALUES_TYPE_KEY": "${valuesType.toString}",
         |"$VALUES_KEY": "$filePath"
         |}
         |""".stripMargin
    }

  }

  val TYPE_KEY = "type"
  val VALUES_TYPE_KEY = "values_type"
  val KEY_VALUES_KEY = "key_values"
  val MAPPED_VALUES_KEY = "mapped_values"
  val KEY_MAPPING_ASSIGNMENTS_KEY = "key_mapping_assignments"
  val NAME_KEY = "name"
  val VALUES_KEY = "values"
  val COLUMN_DELIMITER_KEY = "column_delimiter"
  val KEY_COLUMN_INDEX_KEY = "key_column_index"
  val VALUE_COLUMN_INDEX_KEY = "value_column_index"
  val DIRECTORY_KEY = "directory"
  val FILES_SUFFIX_KEY = "files_suffix"
  val PARAMETER_VALUES_TYPE_KEY = "PARAMETER_VALUES_TYPE"
  val FROM_ORDERED_VALUES_TYPE = "FROM_ORDERED_VALUES_TYPE"
  val JSON_VALUES_MAPPING_TYPE = "JSON_VALUES_MAPPING_TYPE"
  val JSON_VALUES_FILES_MAPPING_TYPE = "JSON_VALUES_FILES_MAPPING_TYPE"
  val JSON_SINGLE_MAPPINGS_TYPE = "JSON_SINGLE_MAPPINGS_TYPE"
  val JSON_ARRAY_MAPPINGS_TYPE = "JSON_ARRAY_MAPPINGS_TYPE"
  val CSV_MAPPINGS_TYPE = "CSV_MAPPING_TYPE"
  val FILE_PREFIX_TO_FILE_LINES_MAPPING_TYPE = "FILE_PREFIX_TO_FILE_LINES_TYPE"


  /**
    * Reusing below formats for the general trait ValuesSeqGenProvider
    */
  implicit object ValueSeqGenProviderFormat extends JsonFormat[ValueSeqGenProvider] with WithStructDef {
    override def read(json: JsValue): ValueSeqGenProvider = {
      try {
        ParameterValuesFormat.read(json)
      }
      catch {
        case _: MatchError => ParameterValueMappingFormat.read(json)
      }
    }

    override def write(obj: ValueSeqGenProvider): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      ParameterValuesFormat.structDef
    }
  }

  /**
    * Allows passing of arbitrary combinations of either single value generators
    * (ParameterValues instances) or mappings (ParameterValueMappings instances),
    * creates overall generator of value sequences
    */
  implicit val parameterValuesGenSeqToValueSeqGeneratorFormat: RootJsonFormat[ParameterValuesGenSeqToValueSeqGenerator] = jsonFormat((values: Seq[ValueSeqGenProvider]) => ParameterValuesGenSeqToValueSeqGenerator.apply(values), "values")

  /**
    * Format for creation of ParameterValues (Seq of values for single type and name)
    */
  implicit object ParameterValuesFormat extends JsonFormat[ParameterValues] with WithStructDef {
    override def read(json: JsValue): ParameterValues = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        // for this case, can use any OrderedValues specification (see respective JsonProtocol implementation)
        case FROM_ORDERED_VALUES_TYPE =>
          val values = fields(VALUES_KEY).convertTo[OrderedValues[String]]
          val parameterValuesType = fields(VALUES_TYPE_KEY).convertTo[ValueType.Value]
          ParameterValues(
            values.name,
            parameterValuesType,
            ByFunctionNrLimitedIndexedGenerator.createFromSeq(values.getAll)
          )
        // define values by plain value passing. Needs list of values, name and type of parameter
        case PARAMETER_VALUES_TYPE_KEY =>
          val values = fields(VALUES_KEY).convertTo[Seq[String]]
          val name = fields(NAME_KEY).convertTo[String]
          val parameterValuesType = fields(VALUES_TYPE_KEY).convertTo[ValueType.Value]
          ParameterValues(
            name,
            parameterValuesType,
            ByFunctionNrLimitedIndexedGenerator.createFromSeq(values)
          )
      }
    }

    override def write(obj: ParameterValues): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              FROM_ORDERED_VALUES_TYPE,
              PARAMETER_VALUES_TYPE_KEY
            )),
            required = true),
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              FROM_ORDERED_VALUES_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(VALUES_KEY),
                  OrderedValuesStringFormat.structDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(VALUES_TYPE_KEY),
                  valueTypeFormat.structDef,
                  required = true
                )
              ),
              PARAMETER_VALUES_TYPE_KEY -> Seq(
                FieldDef(
                  StringConstantStructDef(VALUES_KEY),
                  StringSeqStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(NAME_KEY),
                  StringStructDef,
                  required = true
                ),
                FieldDef(
                  StringConstantStructDef(VALUES_TYPE_KEY),
                  valueTypeFormat.structDef,
                  required = true
                )
              )
            )
          )
        )
      )
    }
  }

  def jsValueStringConversion(jsValue: JsValue): String = jsValue match {
    case e: JsString => e.convertTo[String]
    case e => e.toString()
  }

  /**
    * Format for mappings, defined by the values (Seq[String]) that hold for a given
    * key
    */
  implicit object MappedParameterValuesFormat extends JsonFormat[MappedParameterValues] with WithStructDef {
    override def read(json: JsValue): MappedParameterValues = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        // this case assumes passing of the key values to valid values for the parameter
        case JSON_VALUES_MAPPING_TYPE =>
          val name = fields(NAME_KEY).convertTo[String]
          val valueType = fields(VALUES_TYPE_KEY).convertTo[ValueType.Value]
          val mappings = fields(VALUES_KEY).convertTo[Map[String, Seq[String]]]
          MappedParameterValues(name, valueType, mappings.map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2))))
        // this case assumes a json for the values key, containing mapping
        // of key values to files containing the valid values for the key
        case JSON_VALUES_FILES_MAPPING_TYPE =>
          val name = fields(NAME_KEY).convertTo[String]
          val valueType = fields(VALUES_TYPE_KEY).convertTo[ValueType.Value]
          val fileMappings = fields(VALUES_KEY).convertTo[Map[String, String]]
          val keyToValuesMap: Map[String, IndexedGenerator[String]] = fileMappings.map(x => {
            (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(FileReaderUtils.loadLinesFromFile(x._2, AppConfig.persistenceModule.persistenceDIModule.reader)))
          })
          MappedParameterValues(name, valueType, keyToValuesMap)
        // assumes a json with full key value mappings under the values key
        case JSON_SINGLE_MAPPINGS_TYPE =>
          val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
          val name = fields(NAME_KEY).convertTo[String]
          val valueType = fields(VALUES_TYPE_KEY).convertTo[ValueType.Value]
          val mappingsJsonFile = fields(VALUES_KEY).convertTo[String]
          val jsonMapping = FileReaderUtils.readJsonMapping(mappingsJsonFile, fileReader, x => jsValueStringConversion(x))
            .map(x => (x._1, Seq(x._2)))
            .map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2)))
          MappedParameterValues(name, valueType, jsonMapping)
        case JSON_ARRAY_MAPPINGS_TYPE =>
          try {
            val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
            val name = fields(NAME_KEY).convertTo[String]
            val valueType = fields(VALUES_TYPE_KEY).convertTo[ValueType.Value]
            val mappingsJsonFile = fields(VALUES_KEY).convertTo[String]
            val jsonMapping = FileReaderUtils.readJsonMapping(mappingsJsonFile, fileReader, x => x.convertTo[Seq[JsValue]].map(x => jsValueStringConversion(x)))
              .map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2)))
            MappedParameterValues(name, valueType, jsonMapping)
          }
          catch {
            case e: Throwable => logger.error("failed reading json file of format 'JSON_ARRAY_MAPPINGS_TYPE'", e)
              throw e
          }
        // reading file prefixes in folder, and creating mapping for each where prefix is key and values are the values
        // given in the file, one value per line
        case FILE_PREFIX_TO_FILE_LINES_MAPPING_TYPE =>
          val directory = fields(DIRECTORY_KEY).convertTo[String]
          val filesSuffix: String = fields(FILES_SUFFIX_KEY).convertTo[String]
          val name = fields(NAME_KEY).convertTo[String]
          val valueType = fields(VALUES_TYPE_KEY).convertTo[ValueType.Value]
          val mapping = FileReaderUtils.extractFilePrefixToLineValuesMapping(directory, filesSuffix, "/")
            .map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2)))
          MappedParameterValues(name, valueType, mapping)
        // picking mappings from csv with a key column and a value column. If multiple distinct values are contained
        // for a key, they will be preserved as distinct values in the generator per key value
        case CSV_MAPPINGS_TYPE =>
          val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
          val name = fields(NAME_KEY).convertTo[String]
          val valueType = fields(VALUES_TYPE_KEY).convertTo[ValueType.Value]
          val mappingsCsvFile = fields(VALUES_KEY).convertTo[String]
          val columnDelimiter = fields(COLUMN_DELIMITER_KEY).convertTo[String]
          val keyColumnIndex = fields(KEY_COLUMN_INDEX_KEY).convertTo[Int]
          val valueColumnIndex = fields(VALUE_COLUMN_INDEX_KEY).convertTo[Int]
          val keyValuesMapping = FileReaderUtils.multiMappingFromCSVFile[String](
            source = fileReader.getSource(mappingsCsvFile),
            columnDelimiter = columnDelimiter,
            filterLessColumnsThan = math.max(keyColumnIndex, valueColumnIndex) + 1,
            valsToKey = x => x(keyColumnIndex),
            columnsToValue = x => x(valueColumnIndex))
          MappedParameterValues(name, valueType, keyValuesMapping.map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2.toSeq))))
      }
    }

    override def write(obj: MappedParameterValues): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              JSON_VALUES_MAPPING_TYPE,
              JSON_VALUES_FILES_MAPPING_TYPE,
              JSON_SINGLE_MAPPINGS_TYPE,
              JSON_ARRAY_MAPPINGS_TYPE,
              FILE_PREFIX_TO_FILE_LINES_MAPPING_TYPE,
              CSV_MAPPINGS_TYPE
            )),
            required = true
          ),
          FieldDef(
            StringConstantStructDef(NAME_KEY),
            StringStructDef,
            required = true
          ),
          FieldDef(
            StringConstantStructDef(VALUES_TYPE_KEY),
            valueTypeFormat.structDef,
            required = true
          )
        ),
        Seq(
          ConditionalFields(TYPE_KEY, Map(
            JSON_VALUES_MAPPING_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                MapStructDef(
                  StringStructDef,
                  StringSeqStructDef
                ),
                required = true
              )
            ),
            JSON_VALUES_FILES_MAPPING_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                MapStructDef(
                  StringStructDef,
                  StringStructDef
                ),
                required = true
              )
            ),
            JSON_SINGLE_MAPPINGS_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                StringStructDef,
                required = true
              )
            ),
            JSON_ARRAY_MAPPINGS_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                StringStructDef,
                required = true
              )
            ),
            FILE_PREFIX_TO_FILE_LINES_MAPPING_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(DIRECTORY_KEY),
                StringStructDef,
                required = true
              ),
              FieldDef(
                StringConstantStructDef(FILES_SUFFIX_KEY),
                StringStructDef,
                required = true
              )
            ),
            CSV_MAPPINGS_TYPE -> Seq(
              FieldDef(
                StringConstantStructDef(VALUES_KEY),
                StringStructDef,
                required = true
              ),
              FieldDef(
                StringConstantStructDef(COLUMN_DELIMITER_KEY),
                StringStructDef,
                required = true
              ),
              FieldDef(
                StringConstantStructDef(KEY_COLUMN_INDEX_KEY),
                IntMinMaxStructDef(0, Int.MaxValue),
                required = true
              ),
              FieldDef(
                StringConstantStructDef(VALUE_COLUMN_INDEX_KEY),
                IntMinMaxStructDef(0, Int.MaxValue),
                required = true
              )
            )
          ))
        )
      )
    }
  }

  /**
    * Uses a key generator and one or multiple MappedParameterValues passed as Seq,
    * where the first in the Seq maps to the key generator and all others can either
    * map to the key generator or any mapped value that occurs before itself in the Seq
    *
    * Here a use-case could be having distinct valid parameter values (such as queries) per
    * userId, in which case userId would be used as key generator and queries would be
    * represented by a mapping
    */
  implicit object ParameterValueMappingFormat extends JsonFormat[ParameterValueMapping] with WithStructDef {
    override def read(json: JsValue): ParameterValueMapping = json match {
      case spray.json.JsObject(fields) => {
        val keyValues = fields(KEY_VALUES_KEY).convertTo[ParameterValues]
        val mappedValues = fields(MAPPED_VALUES_KEY).convertTo[Seq[MappedParameterValues]]
        val keyForMappingAssignments = fields(KEY_MAPPING_ASSIGNMENTS_KEY).convertTo[Seq[(Int, Int)]]
        new ParameterValueMapping(keyValues, mappedValues, keyForMappingAssignments)
      }
    }

    override def write(obj: ParameterValueMapping): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(StringConstantStructDef(KEY_VALUES_KEY), ParameterValuesFormat.structDef, required = true),
          FieldDef(
            StringConstantStructDef(MAPPED_VALUES_KEY),
            GenericSeqStructDef(MappedParameterValuesFormat.structDef),
            required = true
          ),
          FieldDef(
            StringConstantStructDef(KEY_MAPPING_ASSIGNMENTS_KEY),
            GenericSeqStructDef(IntSeqStructDef),
            required = true
          )
        ),
        Seq.empty
      )
    }
  }

}
