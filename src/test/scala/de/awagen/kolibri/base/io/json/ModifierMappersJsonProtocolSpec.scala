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

import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.base.io.json.ModifierMappersJsonProtocol._
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.ModifierMappers.{BodyMapper, HeadersMapper, MappingModifier, ParamsMapper}
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import spray.json._


class ModifierMappersJsonProtocolSpec extends UnitTestSpec {

  val paramsMapperJson: JsValue =
    """
      |{
      |"replace": true,
      |"values": {
      | "key1": {
      |   "type": "BY_MAPSEQ",
      |   "values": [
      |     {"test1": ["0.10", "0.11"]},
      |     {"test2": ["0.21", "0.22"]}
      |   ]
      | },
      | "key2": {
      |   "type": "BY_MAPSEQ",
      |   "values": [
      |     {"test1": ["0.4", "0.41"]},
      |     {"test2": ["0.5", "0.55"]}
      |   ]
      | }
      |}
      |}
      |""".stripMargin.parseJson

  val headersMapperJson: JsValue =
    """
      |{
      |"replace": true,
      |"values": {
      | "key1": {
      |   "type": "BY_VALUES_SEQ",
      |   "values": [
      |     {"key1": "value1", "key2": "value2"},
      |     {"key1": "value3", "key2": "value4"}
      |   ]
      | },
      | "key2": {
      |   "type": "BY_VALUES_SEQ",
      |   "values": [
      |     {"key1": "value8", "key2": "value9"},
      |     {"key1": "value10", "key2": "value11"}
      |   ]
      | }
      |}
      |}
      |""".stripMargin.parseJson

  val bodyMapperJson: JsValue =
    """
      |{
      |"values": {
      | "key1": {"type": "BY_VALUES_SEQ", "values": ["val1", "val2"]},
      | "key2": {"type": "BY_VALUES_SEQ", "values": ["val3", "val4"]}
      |}
      |}
      |""".stripMargin.parseJson

  // here we get one key that has a match in all mappings and one that only occurs in one,
  // resulting in 10 overall permutations
  val mappingModifierJson: JsValue =
  """
    |{
    |"keys": {"type": "BY_VALUES_SEQ", "values": ["key1", "key2"]},
    |"paramsMapper": {
    | "replace": true,
    | "values": {
    |   "key1": {
    |     "type": "BY_MAPSEQ",
    |     "values": [
    |       {"test1": ["0.10", "0.11"]},
    |       {"test2": ["0.21", "0.22"]}
    |     ]
    |   }
    | }
    |},
    |"headerMapper": {
    | "replace": true,
    | "values": {
    |   "key1": {
    |     "type": "BY_VALUES_SEQ",
    |     "values": [
    |       {"key1": "value1", "key2": "value2"},
    |       {"key1": "value3", "key2": "value4"}
    |     ]
    |   }
    | }
    |},
    |"bodyMapper": {
    | "values": {
    |   "key1": {"type": "BY_VALUES_SEQ", "values": ["val1", "val2"]},
    |   "key2": {"type": "BY_VALUES_SEQ", "values": ["val3", "val4"]}
    | }
    |}
    |}
    |""".stripMargin.parseJson

  // here the keys dont match any mapping, thus we dont expect any modifier to come out of it
  // when parsing to MappingModifier
  val mappingModifierWithNonMatchingKeysJson: JsValue =
  """
    |{
    |"keys": {"type": "BY_VALUES_SEQ", "values": ["nonmatching1", "nonmatching2"]},
    |"paramsMapper": {
    | "replace": true,
    | "values": {
    |   "key1": {
    |     "type": "BY_MAPSEQ",
    |     "values": [
    |       {"test1": ["0.10", "0.11"]},
    |       {"test2": ["0.21", "0.22"]}
    |     ]
    |   }
    | }
    |},
    |"headerMapper": {
    | "replace": true,
    | "values": {
    |   "key1": {
    |     "type": "BY_VALUES_SEQ",
    |     "values": [
    |       {"key1": "value1", "key2": "value2"},
    |       {"key1": "value3", "key2": "value4"}
    |     ]
    |   }
    | }
    |},
    |"bodyMapper": {
    | "values": {
    |   "key1": {"type": "BY_VALUES_SEQ", "values": ["val1", "val2"]},
    |   "key2": {"type": "BY_VALUES_SEQ", "values": ["val3", "val4"]}
    | }
    |}
    |}
    |""".stripMargin.parseJson

  val onlyKeysMappingModifierJson: JsValue =
    """{"keys": {"type": "BY_VALUES_SEQ", "values": ["val1", "val2"]}}""".stripMargin.parseJson

  "ModifierMappersJsonProtocol" must {

    "correctly parse ParamsMapper" in {
      // given, when
      val mapper = paramsMapperJson.convertTo[ParamsMapper]
      // then
      mapper.replace mustBe true
      mapper.map.keys.toSeq mustBe Seq("key1", "key2")
      val value1: Seq[Map[String, Seq[String]]] = mapper.map("key1").iterator.toSeq
      val value2: Seq[Map[String, Seq[String]]] = mapper.map("key2").iterator.toSeq
      value1 mustBe Seq(Map("test1" -> Seq("0.10", "0.11")), Map("test2" -> Seq("0.21", "0.22")))
      value2 mustBe Seq(Map("test1" -> Seq("0.4", "0.41")), Map("test2" -> Seq("0.5", "0.55")))
    }

    "correctly parse HeadersMapper" in {
      // given, when
      val mapper = headersMapperJson.convertTo[HeadersMapper]
      // then
      mapper.replace mustBe true
      mapper.map.keys.toSeq mustBe Seq("key1", "key2")
      val value1: Seq[Map[String, String]] = mapper.map("key1").iterator.toSeq
      val value2: Seq[Map[String, String]] = mapper.map("key2").iterator.toSeq
      value1 mustBe Seq(Map("key1" -> "value1", "key2" -> "value2"), Map("key1" -> "value3", "key2" -> "value4"))
      value2 mustBe Seq(Map("key1" -> "value8", "key2" -> "value9"), Map("key1" -> "value10", "key2" -> "value11"))
    }

    "correctly parse BodyMapper" in {
      // given, when
      val mapper = bodyMapperJson.convertTo[BodyMapper]
      // then
      mapper.map.keys.size mustBe 2
      val value1: Seq[String] = mapper.map("key1").iterator.toSeq
      val value2: Seq[String] = mapper.map("key2").iterator.toSeq
      value1 mustBe Seq("val1", "val2")
      value2 mustBe Seq("val3", "val4")
    }

    "correctly parse MappingModifier" in {
      val mappingModifier = mappingModifierJson.convertTo[MappingModifier]
      val modifiers: Seq[Modifier[RequestTemplateBuilder]] = mappingModifier.modifiers.iterator.toSeq
      modifiers.size mustBe 10
    }

    "correctly parse MappingModifier with non-matching keys to empty modifier" in {
      val mappingModifier = mappingModifierWithNonMatchingKeysJson.convertTo[MappingModifier]
      val modifiers: Seq[Modifier[RequestTemplateBuilder]] = mappingModifier.modifiers.iterator.toSeq
      modifiers.size mustBe 0
    }
  }

}
