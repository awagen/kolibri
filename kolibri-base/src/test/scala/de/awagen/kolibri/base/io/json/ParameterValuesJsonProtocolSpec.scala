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

import de.awagen.kolibri.base.io.json.ParameterValuesJsonProtocol.{MappedParameterValuesFormat, ParameterValueMappingConfigFormat, ParameterValuesConfigFormat, ValueSeqGenConfigFormat}
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ValueSeqGenConfigImplicits.ValueSeqGenConfigImplicits
import de.awagen.kolibri.base.processing.modifiers.ParameterValues._
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import spray.json._

class ParameterValuesJsonProtocolSpec extends UnitTestSpec {

  object ParameterValuesJsonDefinitions {
    val parameterValuesFromOrderedValuesJson: String =
      """
        |{
        |"type": "FROM_ORDERED_VALUES_TYPE",
        |"values": {"type": "FROM_VALUES_TYPE", "name": "param1", "values": ["key1", "key2", "key3"]},
        |"values_type": "URL_PARAMETER"
        |}
        |""".stripMargin

    val parameterValuesFromOrderedValuesAsValueSeqGenProviderJson: String =
      s"""
         |{
         |"type": "STANDALONE",
         |"values": $parameterValuesFromOrderedValuesJson
         |}
         |""".stripMargin

    val parameterValuesPassedJson: String =
      """
        |{
        |"type": "PARAMETER_VALUES_TYPE",
        |"name": "param1",
        |"values": ["value1", "value2"],
        |"values_type": "URL_PARAMETER"
        |}
        |""".stripMargin

    val parameterValuesFromOrderedValuesFromFileJson: String = {
      """
        |{
        |"type": "FROM_ORDERED_VALUES_TYPE",
        |"values_type": "URL_PARAMETER",
        |"values": {
        |  "type": "FROM_FILES_LINES_TYPE",
        |  "name": "q",
        |  "file": "data/queryterms.txt"
        |}
        |}
        |""".stripMargin
    }

    val parameterValuesFromRangeJson: String = {
      """
        |{
        |"type": "FROM_ORDERED_VALUES_TYPE",
        |"values_type": "URL_PARAMETER",
        |"values": {
        |  "type": "FROM_RANGE_TYPE",
        |  "name": "o",
        |  "start": 0.0,
        |  "end": 2000.0,
        |  "stepSize": 1.0
        |}
        |}
        |""".stripMargin
    }

  }

  object MappedParameterValuesDefinitions {

    val jsonFullKeyValuesMappingJson: String =
      """{
        |"type": "JSON_VALUES_MAPPING_TYPE",
        |"name": "mappedParam1",
        |"values_type": "URL_PARAMETER",
        |"values": {
        | "key1": ["key1_val1", "key1_val2"],
        | "key2": ["key2_val1", "key2_val2"]
        |}
        |}
        |""".stripMargin

    val jsonKeyToValuesFileMappingJson: String =
      """{
        |"type": "JSON_VALUES_FILES_MAPPING_TYPE",
        |"name": "mappedParam1",
        |"values_type": "URL_PARAMETER",
        |"values": {
        | "key1": "data/mappedParameterValuesTest/key1Values.txt",
        | "key2": "data/mappedParameterValuesTest/key2Values.txt",
        | "key3": "data/mappedParameterValuesTest/key3Values.txt"
        |}
        |}
        |""".stripMargin

    val jsonKeyToValuesMappingJson: String =
      """{
        |"type": "JSON_ARRAY_MAPPINGS_TYPE",
        |"name": "mappedParam1",
        |"values_type": "URL_PARAMETER",
        |"values": "data/mappedParameterValuesTest/fullKeyValueMappingJson.json"
        |}
        |""".stripMargin

    val filePrefixToLinesValuesMappingJson: String =
      """{
        |"type": "FILE_PREFIX_TO_FILE_LINES_TYPE",
        |"name": "mappedParam1",
        |"values_type": "URL_PARAMETER",
        |"directory": "data/mappedParameterValuesTest",
        |"files_suffix": ".txt"
        |}
        |""".stripMargin

    val filePrefixToLinesValuesMappingJson2: String =
      """{
        |"type": "FILE_PREFIX_TO_FILE_LINES_TYPE",
        |"name": "mappedParam1",
        |"values_type": "URL_PARAMETER",
        |"directory": "data/fileMappingValueSeqTest",
        |"files_suffix": ".txt"
        |}
        |""".stripMargin

    val csvMappingJson1: String =
      """{
        |"type": "CSV_MAPPING_TYPE",
        |"name": "csvMappedParam1",
        |"values_type": "URL_PARAMETER",
        |"values": "data/csvMappedParameterTest/mapping1.csv",
        |"column_delimiter": ",",
        |"key_column_index": 0,
        |"value_column_index": 1
        |}
        |""".stripMargin

  }

  object ParameterValueMappingDefinitions {
    val jsonMapping: String =
      s"""
         |{
         |"key_values": ${ParameterValuesJsonDefinitions.parameterValuesFromOrderedValuesJson},
         |"mapped_values": [${MappedParameterValuesDefinitions.jsonFullKeyValuesMappingJson}],
         |"key_mapping_assignments": [[0,1]]
         |}
         |""".stripMargin

    val jsonMappingAsValueSeqGenProvider: String = {
      s"""
         |{
         |"type": "MAPPING",
         |"values": $jsonMapping
         |}
         |""".stripMargin
    }

    val jsonMultiMapping: String =
      s"""
         |{
         |      "key_values": {
         |        "type": "FROM_ORDERED_VALUES_TYPE",
         |        "values": {
         |          "type": "FROM_FILENAME_KEYS_TYPE",
         |          "directory": "data/queries_for_id",
         |          "filesSuffix": ".txt",
         |          "name": "id"
         |        },
         |        "values_type": "URL_PARAMETER"
         |      },
         |      "mapped_values": [
         |        {
         |          "type": "FILE_PREFIX_TO_FILE_LINES_TYPE",
         |          "directory": "data/queries_for_id",
         |          "files_suffix": ".txt",
         |          "name": "q",
         |          "values_type": "URL_PARAMETER"
         |        },
         |        {
         |          "type": "CSV_MAPPING_TYPE",
         |          "name": "section_id",
         |          "values_type": "URL_PARAMETER",
         |          "values": "data/section_for_id/section_for_id.csv",
         |          "column_delimiter": ";",
         |          "key_column_index": 0,
         |          "value_column_index": 1
         |        },
         |        {
         |          "type": "JSON_SINGLE_MAPPINGS_TYPE",
         |          "name": "section_header",
         |          "values_type": "HEADER",
         |          "values": "data/section_headers/section_header_mapping.json"
         |        }
         |      ],
         |      "key_mapping_assignments": [
         |        [0, 1],
         |        [0, 2],
         |        [2, 3]
         |      ]
         |    }
         |""".stripMargin
  }

  "ParameterValuesJsonProtocol" must {

    "parse ParameterValues from OrderedValues" in {
      // given, when
      val values = ParameterValuesJsonDefinitions.parameterValuesFromOrderedValuesJson.parseJson.convertTo[ParameterValuesConfig]
      // then
      values.name mustBe "param1"
      values.valueType mustBe ValueType.URL_PARAMETER
      values.values.iterator.toSeq mustBe Seq("key1", "key2", "key3")
    }

    "parse ParameterValues by passing values" in {
      // given, when
      val values = ParameterValuesJsonDefinitions.parameterValuesPassedJson.parseJson.convertTo[ParameterValuesConfig]
      // then
      values.name mustBe "param1"
      values.valueType mustBe ValueType.URL_PARAMETER
      values.values.iterator.toSeq mustBe Seq("value1", "value2")
    }

    "parse ParameterValues from file" in {
      // given, when
      val values = ParameterValuesJsonDefinitions.parameterValuesFromOrderedValuesFromFileJson.parseJson.convertTo[ParameterValuesConfig]
      // then
      values.name mustBe "q"
      values.valueType mustBe ValueType.URL_PARAMETER
      values.values.iterator.toSeq mustBe Seq("schuh", "spiegel", "uhr", "hose", "jeans", "tv")
    }

    "parse ParameterValues from range" in {
      // given, when
      val valuesConfig = ParameterValuesJsonDefinitions.parameterValuesFromRangeJson.parseJson.convertTo[ParameterValuesConfig]
      val values = valuesConfig.toProvider.asInstanceOf[ParameterValues]
      // then
      values.name mustBe "o"
      values.valueType mustBe ValueType.URL_PARAMETER
      values.values.size mustBe 2001
      values.get(0).get.value.toDouble.toInt mustBe 0
      values.get(2000).get.value.toDouble.toInt mustBe 2000
    }


    "parse MappedParameterValues from json values mapping" in {
      // given, when
      val values = MappedParameterValuesDefinitions.jsonFullKeyValuesMappingJson.parseJson.convertTo[MappedParameterValues]
      // then
      values.name mustBe "mappedParam1"
      values.valueType mustBe ValueType.URL_PARAMETER
      values.values.map(x => (x._1, x._2.iterator.toSeq)) mustBe Map("key1" -> Seq("key1_val1", "key1_val2"), "key2" -> Seq("key2_val1", "key2_val2"))
    }

    "parse MappedParameterValues from json values via file mapping" in {
      // given, when
      val values = MappedParameterValuesDefinitions.jsonKeyToValuesFileMappingJson.parseJson.convertTo[MappedParameterValues]
      // then
      values.name mustBe "mappedParam1"
      values.valueType mustBe ValueType.URL_PARAMETER
      values.values.keySet mustBe Set("key1", "key2", "key3")
      values.values("key1").iterator.toSeq mustBe Seq("key1Value1", "key1Value2")
      values.values("key2").iterator.toSeq mustBe Seq("key2Value1", "key2Value2", "key2Value3")
      values.values("key3").iterator.toSeq mustBe Seq("key3Value1")
    }

    "parse MappedParameterValues from json key value mappings in json file" in {
      // given, when
      val values = MappedParameterValuesDefinitions.jsonKeyToValuesMappingJson.parseJson.convertTo[MappedParameterValues]
      // then
      values.name mustBe "mappedParam1"
      values.valueType mustBe ValueType.URL_PARAMETER
      values.values.map(x => (x._1, x._2.iterator.toSeq)) mustBe Map("key1" -> Seq("v1", "v2", "v3"), "key2" -> Seq("v1"), "key3" -> Seq("v5", "v6"))
    }

    "parse MappedParameterValues using file prefix as key and valid line values as values" in {
      // given, when
      val values = MappedParameterValuesDefinitions.filePrefixToLinesValuesMappingJson.parseJson.convertTo[MappedParameterValues]
      // then
      values.name mustBe "mappedParam1"
      values.valueType mustBe ValueType.URL_PARAMETER
      values.values.map(x => (x._1, x._2.iterator.toSeq)) mustBe Map(
        "key1Values" -> Seq("key1Value1", "key1Value2"),
        "key2Values" -> Seq("key2Value1", "key2Value2", "key2Value3"),
        "key3Values" -> Seq("key3Value1"))
    }

    "parse MappedParameterValues using file prefix as key and valid line values as values - part2" in {
      // given, when
      val values = MappedParameterValuesDefinitions.filePrefixToLinesValuesMappingJson2.parseJson.convertTo[MappedParameterValues]
      // then
      values.values.map(x => (x._1, x._2.iterator.toSeq)) mustBe Map(
        "key1" -> Seq("val1_1,val1_2", "val1_3", "val1_4"),
        "key2" -> Seq("val2_1", "val2_2"),
        "key3" -> Seq("val3_1", "val3_2", "val3_3", "val3_4"),
        "key4" -> Seq("val4_1, val4_2", "val4_3,  val4_4",
          "val4_5, val4_6, val4_7",
          "val4_8")
      )
    }

    "parse csv based mapping" in {
      //given, when
      val values = MappedParameterValuesDefinitions.csvMappingJson1.parseJson.convertTo[MappedParameterValues]
      // then
      values.values.map(x => (x._1, x._2.iterator.toSet)) mustBe Map(
        "key1" -> Set("value1", "value2", "value3"),
        "key2" -> Set("value1"),
        "key3" -> Set("value2")
      )
    }

    "parse ParameterValueMapping" in {
      // given, when
      val valuesConfig = ParameterValueMappingDefinitions.jsonMapping.parseJson.convertTo[ParameterValueMappingConfig]
      val values = valuesConfig.toProvider.asInstanceOf[ParameterValueMapping]
      // then
      values.nrOfElements mustBe 4
      values.get(0).get mustBe Seq(ParameterValue("param1", ValueType.URL_PARAMETER, "key1"), ParameterValue("mappedParam1", ValueType.URL_PARAMETER, "key1_val1"))
      values.get(1).get mustBe Seq(ParameterValue("param1", ValueType.URL_PARAMETER, "key1"), ParameterValue("mappedParam1", ValueType.URL_PARAMETER, "key1_val2"))
      values.get(2).get mustBe Seq(ParameterValue("param1", ValueType.URL_PARAMETER, "key2"), ParameterValue("mappedParam1", ValueType.URL_PARAMETER, "key2_val1"))
      values.get(3).get mustBe Seq(ParameterValue("param1", ValueType.URL_PARAMETER, "key2"), ParameterValue("mappedParam1", ValueType.URL_PARAMETER, "key2_val2"))
    }

    "parse ParameterValueMapping multiple mapping" in {
      // given, when, then
      ParameterValueMappingDefinitions.jsonMultiMapping.parseJson.convertTo[ParameterValueMappingConfig]
        .toProvider.asInstanceOf[ParameterValueMapping].nrOfElements mustBe 17
    }

    "parse ValueSeqGenProvider" in {
      // given, when
      val parameterValues = ParameterValuesJsonDefinitions.parameterValuesFromOrderedValuesAsValueSeqGenProviderJson.parseJson.convertTo[ValueSeqGenConfig].toProvider
      val mapping = ParameterValueMappingDefinitions.jsonMappingAsValueSeqGenProvider.parseJson.convertTo[ValueSeqGenConfig].toProvider
      // then
      parameterValues.isInstanceOf[ParameterValues] mustBe true
      mapping.isInstanceOf[ParameterValueMapping] mustBe true
    }

  }

}
