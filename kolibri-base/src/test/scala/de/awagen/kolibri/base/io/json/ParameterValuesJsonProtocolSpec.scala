package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.io.json.ParameterValuesJsonProtocol.{MappedParameterValuesFormat, ParameterValueMappingFormat, ParameterValuesFormat, ValueSeqGenProviderFormat}
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.{MappedParameterValues, ParameterValue, ParameterValueMapping, ParameterValues, ValueSeqGenProvider, ValueType}
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import spray.json._

class ParameterValuesJsonProtocolSpec extends UnitTestSpec {

  object ParameterValuesJsonDefinitions {
    val parameterValuesFromOrderedValuesJson: String =
      """
        |{
        |"type": "FROM_ORDERED_VALUES",
        |"values": {"type": "FROM_VALUES", "name": "param1", "values": ["key1", "key2", "key3"]},
        |"values_type": "URL_PARAMETER"
        |}
        |""".stripMargin

    val parameterValuesPassedJson: String =
      """
        |{
        |"type": "parameter_values_type",
        |"name": "param1",
        |"values": ["value1", "value2"],
        |"values_type": "URL_PARAMETER"
        |}
        |""".stripMargin
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
        |"type": "JSON_MAPPINGS_TYPE",
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
  }

  "ParameterValuesJsonProtocol" must {

    "parse ParameterValues from OrderedValues" in {
      // given, when
      val values = ParameterValuesJsonDefinitions.parameterValuesFromOrderedValuesJson.parseJson.convertTo[ParameterValues]
      // then
      values.name mustBe "param1"
      values.valueType mustBe ValueType.URL_PARAMETER
      values.values.iterator.toSeq mustBe Seq("key1", "key2", "key3")
    }

    "parse ParameterValues by passing values" in {
      // given, when
      val values = ParameterValuesJsonDefinitions.parameterValuesPassedJson.parseJson.convertTo[ParameterValues]
      // then
      values.name mustBe "param1"
      values.valueType mustBe ValueType.URL_PARAMETER
      values.values.iterator.toSeq mustBe Seq("value1", "value2")
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
      val values = ParameterValueMappingDefinitions.jsonMapping.parseJson.convertTo[ParameterValueMapping]
      // then
      values.nrOfElements mustBe 4
      values.get(0).get mustBe Seq(ParameterValue("param1", ValueType.URL_PARAMETER, "key1"), ParameterValue("mappedParam1", ValueType.URL_PARAMETER, "key1_val1"))
      values.get(1).get mustBe Seq(ParameterValue("param1", ValueType.URL_PARAMETER, "key1"), ParameterValue("mappedParam1", ValueType.URL_PARAMETER, "key1_val2"))
      values.get(2).get mustBe Seq(ParameterValue("param1", ValueType.URL_PARAMETER, "key2"), ParameterValue("mappedParam1", ValueType.URL_PARAMETER, "key2_val1"))
      values.get(3).get mustBe Seq(ParameterValue("param1", ValueType.URL_PARAMETER, "key2"), ParameterValue("mappedParam1", ValueType.URL_PARAMETER, "key2_val2"))
    }

    "parse ValueSeqGenProvider" in {
      // given, when
      val parameterValues = ParameterValuesJsonDefinitions.parameterValuesFromOrderedValuesJson.parseJson.convertTo[ValueSeqGenProvider]
      val mapping = ParameterValueMappingDefinitions.jsonMapping.parseJson.convertTo[ValueSeqGenProvider]
      // then
      parameterValues.isInstanceOf[ParameterValues] mustBe true
      mapping.isInstanceOf[ParameterValueMapping] mustBe true
    }

  }

}
