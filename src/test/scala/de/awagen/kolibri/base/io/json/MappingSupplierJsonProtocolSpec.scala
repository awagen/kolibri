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

import de.awagen.kolibri.base.testclasses.UnitTestSpec
import spray.json._
import MappingSupplierJsonProtocol._
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator

class MappingSupplierJsonProtocolSpec extends UnitTestSpec {

  "MappingSupplierJsonProtocol" must {

    "provide supplier of Map[String, String] from json" in {
      // given
      val json =
        """
          |{
          | "type": "FROM_JSON_MAP",
          | "value": {
          |   "key1": "value1",
          |   "key2": "value2"
          | }
          |}
          |""".stripMargin.parseJson
      // when
      val supplier: () => Map[String, String] = json.convertTo[() => Map[String, String]]
      val value: Map[String, String] = supplier.apply()
      // then
      value mustBe Map("key1" -> "value1", "key2" -> "value2")
    }

    "provide supplier of Map[String, String] from csv" in {
      // given
      val json: JsValue =
        """
          |{
          | "type": "FROM_CSV",
          | "filePath": "data/mappingTest.csv",
          | "columnSeparator": ",",
          | "fromIndex": 1,
          | "toIndex": 2,
          | "ignoreFirstLine": false
          |}
          |""".stripMargin.parseJson
      // when
      val supplier: () => Map[String, String] = json.convertTo[() => Map[String, String]]
      val value: Map[String, String] = supplier.apply()
      // then
      value mustBe Map(
        "key1" -> "val1",
        "key2" -> "val2",
        "key3" -> "val3",
        "key4" -> "val4",
        "key5" -> "val5"
      )
    }

    "provide supplier of Map[String, Map[String, IndexedGenerator[Seq[String]]]] from json" in {
      // given
      val json =
        """
          |{
          | "type": "FROM_JSON_MAP",
          | "value": {
          |   "key1": {
          |     "param1": {
          |       "type": "BY_VALUES_SEQ",
          |       "values": [["v1"], ["v2"]]
          |     }
          |   },
          |   "key2": {
          |     "param2": {
          |       "type": "BY_VALUES_SEQ",
          |       "values": [["a1", "a2"], ["a3"]]
          |     }
          |   }
          | }
          |}
          |""".stripMargin.parseJson
      // when
      val supplier = json.convertTo[() => Map[String, Map[String, IndexedGenerator[Seq[String]]]]]
      val value: Map[String, Map[String, IndexedGenerator[Seq[String]]]] = supplier.apply()
      val valueKey1: Map[String, IndexedGenerator[Seq[String]]] = value("key1")
      val valueKey2: Map[String, IndexedGenerator[Seq[String]]] = value("key2")
      val valueKey1Param1: Seq[Seq[String]] = valueKey1("param1").iterator.toSeq
      val valueKey2Param2: Seq[Seq[String]] = valueKey2("param2").iterator.toSeq
      // then
      value.keySet mustBe Set("key1", "key2")
      valueKey1.keySet mustBe Set("param1")
      valueKey2.keySet mustBe Set("param2")
      valueKey1Param1 mustBe Seq(Seq("v1"), Seq("v2"))
      valueKey2Param2 mustBe Seq(Seq("a1", "a2"), Seq("a3"))
    }
  }

  "provide supplier of Map[String, Map[String, IndexedGenerator[Seq[String]]]] from directory files" in {
    // given
    val json =
      """
        |{
        | "type": "FROM_DIRECTORY",
        | "rowSeparator": ",",
        | "paramNamesToDirMap": {
        |   "param1": "data/fileMappingValueSeqTest"
        | },
        | "filesSuffix": ".txt"
        |}
        |""".stripMargin.parseJson
    // when
    val valuesSupplier = json.convertTo[() => Map[String, Map[String, IndexedGenerator[Seq[String]]]]]
    val values: Map[String, Map[String, IndexedGenerator[Seq[String]]]] = valuesSupplier.apply()
    val key1Value: Map[String, Seq[Seq[String]]] = values("key1").view.mapValues(x => x.iterator.toSeq).toMap
    val key2Value: Map[String, Seq[Seq[String]]] = values("key2").view.mapValues(x => x.iterator.toSeq).toMap
    val key3Value: Map[String, Seq[Seq[String]]] = values("key3").view.mapValues(x => x.iterator.toSeq).toMap
    val key4Value: Map[String, Seq[Seq[String]]] = values("key4").view.mapValues(x => x.iterator.toSeq).toMap
    // then
    values.keySet mustBe Set("key1", "key2", "key3", "key4")
    key1Value mustBe Map("param1" -> Seq(Seq("val1_1", "val1_2"), Seq("val1_3"), Seq("val1_4")))
    key2Value mustBe Map("param1" -> Seq(Seq("val2_1"), Seq("val2_2")))
    key3Value mustBe Map("param1" -> Seq(Seq("val3_1"), Seq("val3_2"), Seq("val3_3"), Seq("val3_4")))
    key4Value mustBe Map("param1" -> Seq(Seq("val4_1", "val4_2"), Seq("val4_3", "val4_4"), Seq("val4_5", "val4_6", "val4_7"), Seq("val4_8")))
  }



  "provide supplier of Map[String, Map[String, IndexedGenerator[String]]] from json" in {
    // given
    val json =
      """
        |{
        | "type": "FROM_JSON_MAP",
        | "value": {
        |   "key1": {
        |     "param1": {
        |       "type": "BY_VALUES_SEQ",
        |       "values": ["v1", "v2"]
        |     }
        |   },
        |   "key2": {
        |     "param2": {
        |       "type": "BY_VALUES_SEQ",
        |       "values": ["a1", "a2", "a3"]
        |     }
        |   }
        | }
        |}
        |""".stripMargin.parseJson
    // when
    val supplier = json.convertTo[() => Map[String, Map[String, IndexedGenerator[String]]]]
    val value: Map[String, Map[String, IndexedGenerator[String]]] = supplier.apply()
    val valueKey1: Map[String, IndexedGenerator[String]] = value("key1")
    val valueKey2: Map[String, IndexedGenerator[String]] = value("key2")
    val valueKey1Param1: Seq[String] = valueKey1("param1").iterator.toSeq
    val valueKey2Param2: Seq[String] = valueKey2("param2").iterator.toSeq
    // then
    value.keySet mustBe Set("key1", "key2")
    valueKey1.keySet mustBe Set("param1")
    valueKey2.keySet mustBe Set("param2")
    valueKey1Param1 mustBe Seq("v1", "v2")
    valueKey2Param2 mustBe Seq("a1", "a2", "a3")
  }

  "provide supplier of Map[String, Map[String, IndexedGenerator[String]]] from directory files" in {
    // given
    val json =
      """
        |{
        | "type": "FROM_DIRECTORY",
        | "paramNamesToDirMap": {
        |   "param1": "data/fileMappingSingleValueTest"
        | },
        | "filesSuffix": ".txt"
        |}
        |""".stripMargin.parseJson
    // when
    val valuesSupplier = json.convertTo[() => Map[String, Map[String, IndexedGenerator[String]]]]
    val values: Map[String, Map[String, IndexedGenerator[String]]] = valuesSupplier.apply()
    val key1Value: Map[String, Seq[String]] = values("key1").view.mapValues(x => x.iterator.toSeq).toMap
    val key2Value: Map[String, Seq[String]] = values("key2").view.mapValues(x => x.iterator.toSeq).toMap
    val key3Value: Map[String, Seq[String]] = values("key3").view.mapValues(x => x.iterator.toSeq).toMap
    // then
    values.keySet mustBe Set("key1", "key2", "key3")
    key1Value mustBe Map("param1" -> Seq("value1_1,value1_2,value1_3", "value1_4", "value1_5"))
    key2Value mustBe Map("param1" -> Seq("value2_1", "value2_2, value2_3", "value2_4"))
    key3Value mustBe Map("param1" -> Seq("value3_1, value3_2, value3_3", "value3_4,value3_5"))
  }


}
