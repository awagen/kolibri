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
import de.awagen.kolibri.datatypes.values.OrderedValues
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, RootJsonFormat, enrichAny}

object ParameterValuesJsonProtocol extends DefaultJsonProtocol {

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
  val PARAMETER_VALUES_TYPE_KEY = "parameter_values_type"
  val FROM_ORDERED_VALUES_TYPE = "FROM_ORDERED_VALUES"
  val JSON_VALUES_MAPPING_TYPE = "JSON_VALUES_MAPPING_TYPE"
  val JSON_VALUES_FILES_MAPPING_TYPE = "JSON_VALUES_FILES_MAPPING_TYPE"
  val JSON_MAPPINGS_TYPE = "JSON_MAPPINGS_TYPE"
  val CSV_MAPPINGS_TYPE = "CSV_MAPPING_TYPE"
  val FILE_PREFIX_TO_FILE_LINES_MAPPING_TYPE = "FILE_PREFIX_TO_FILE_LINES_TYPE"

  /**
   * Reusing below formats for the general trait ValuesSeqGenProvider
   */
  implicit object ValueSeqGenProviderFormat extends JsonFormat[ValueSeqGenProvider] {
    override def read(json: JsValue): ValueSeqGenProvider = {
      try{
        ParameterValuesFormat.read(json)
      }
      catch {
        case _: MatchError => ParameterValueMappingFormat.read(json)
      }
    }

    override def write(obj: ValueSeqGenProvider): JsValue = """{}""".toJson
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
  implicit object ParameterValuesFormat extends JsonFormat[ParameterValues] {
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
  }

  /**
   * Format for mappings, defined by the values (Seq[String]) that hold for a given
   * key
   */
  implicit object MappedParameterValuesFormat extends JsonFormat[MappedParameterValues] {
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
        case JSON_MAPPINGS_TYPE =>
          val fileReader = AppConfig.persistenceModule.persistenceDIModule.reader
          val name = fields(NAME_KEY).convertTo[String]
          val valueType = fields(VALUES_TYPE_KEY).convertTo[ValueType.Value]
          val mappingsJsonFile = fields(VALUES_KEY).convertTo[String]
          val jsonMapping = FileReaderUtils.readJsonMapping(mappingsJsonFile, fileReader)
            .map(x => (x._1, ByFunctionNrLimitedIndexedGenerator.createFromSeq(x._2)))
          MappedParameterValues(name, valueType, jsonMapping)
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
  implicit object ParameterValueMappingFormat extends JsonFormat[ParameterValueMapping] {
    override def read(json: JsValue): ParameterValueMapping = json match {
      case spray.json.JsObject(fields) => {
        val keyValues = fields(KEY_VALUES_KEY).convertTo[ParameterValues]
        val mappedValues = fields(MAPPED_VALUES_KEY).convertTo[Seq[MappedParameterValues]]
        val keyForMappingAssignments = fields(KEY_MAPPING_ASSIGNMENTS_KEY).convertTo[Seq[(Int, Int)]]
        new ParameterValueMapping(keyValues, mappedValues, keyForMappingAssignments)
      }
    }

    override def write(obj: ParameterValueMapping): JsValue = """{}""".toJson
  }

}
