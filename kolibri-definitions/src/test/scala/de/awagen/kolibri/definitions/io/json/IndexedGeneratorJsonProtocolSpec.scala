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

import de.awagen.kolibri.definitions.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.json.OrderedValuesJsonProtocol.DISTINCT_VALUES_TYPE
import spray.json.{JsValue, _}

class IndexedGeneratorJsonProtocolSpec extends UnitTestSpec {

  import JsonProtocolTestHelper.generatorJsonProtocol._

  val seqValueMapGeneratorFromMultiValues: JsValue =
    s"""
      |{
      |"type": "BY_MULTIVALUES",
      |"values": [
      |{"type": "GRID_FROM_VALUES_SEQ_TYPE", "values":[{"type": "$DISTINCT_VALUES_TYPE", "name": "test1", "values": [0.10, 0.11]}]},
      |{"type": "GRID_FROM_VALUES_SEQ_TYPE", "values":[{"type": "$DISTINCT_VALUES_TYPE", "name": "test2", "values": [0.21, 0.22]}]}
      |]
      |}
      |""".stripMargin.parseJson

  val seqValueMapGeneratorFromSeqOfMaps: JsValue =
    """
      |{
      |"type": "BY_MAPSEQ",
      |"values": [
      |{"test1": ["0.10", "0.11"]},
      |{"test2": ["0.21", "0.22"]}
      |]
      |}
      |""".stripMargin.parseJson

  val singleValueMapGeneratorFromSeqOfMaps: JsValue =
    """
      |{
      |"type": "BY_VALUES_SEQ",
      |"values": [
      |{"key1": "value1", "key2": "value2"},
      |{"key1": "value3", "key2": "value4"}
      |]
      |}
      |""".stripMargin.parseJson

  val singleValueSeqFromStringSeq: JsValue =
    """{
      |"type": "BY_VALUES_SEQ",
      |"values": ["value1", "value2", "value3"]
      |}
      |""".stripMargin.parseJson

  val fromDirectoryFilenames: JsValue =
    """{
      |"type": "BY_FILENAME_KEYS",
      |"directory": "data/fileMappingValueSeqTest",
      |"filesSuffix": ".txt"
      |}
      |""".stripMargin.parseJson

  "IndexedGeneratorJsonProtocol" must {

    "correctly parse IndexedGenerator[Map[String, Seq[String]]] from OrderedMultiValues" in {
      val gen = seqValueMapGeneratorFromMultiValues.convertTo[IndexedGenerator[Map[String, Seq[String]]]]
      val valueSeq = gen.iterator.toSeq
      valueSeq.size mustBe 2
      valueSeq.head mustBe Map("test1" -> Seq("0.1", "0.11"))
      valueSeq(1) mustBe Map("test2" -> Seq("0.21", "0.22"))
    }

    "correctly parse IndexedGenerator[Map[String, Seq[String]]] from Map" in {
      val gen = seqValueMapGeneratorFromSeqOfMaps.convertTo[IndexedGenerator[Map[String, Seq[String]]]]
      val valueSeq = gen.iterator.toSeq
      valueSeq.size mustBe 2
      valueSeq.head mustBe Map("test1" -> Seq("0.10", "0.11"))
      valueSeq(1) mustBe Map("test2" -> Seq("0.21", "0.22"))
    }

    "correctly parse IndexedGenerator[Map[String, String]] by Seq of Maps" in {
      val gen = singleValueMapGeneratorFromSeqOfMaps.convertTo[IndexedGenerator[Map[String, String]]]
      val valueSeq = gen.iterator.toSeq
      valueSeq.size mustBe 2
      valueSeq.head mustBe Map("key1" -> "value1", "key2" -> "value2")
      valueSeq(1) mustBe Map("key1" -> "value3", "key2" -> "value4")
    }

    "correctly parse IndexedGenerator[String] by Seq of Strings" in {
      val gen = singleValueSeqFromStringSeq.convertTo[IndexedGenerator[String]]
      val valueSeq = gen.iterator.toSeq
      valueSeq mustBe Seq("value1", "value2", "value3")
    }

    "correctly parse IndexedGenerator[String] from filenames without suffix" in {
      val gen = fromDirectoryFilenames.convertTo[IndexedGenerator[String]]
      val valuesSeq = gen.iterator.toSeq
      valuesSeq mustBe Seq("key1", "key2", "key3", "key4")
    }

  }

}
