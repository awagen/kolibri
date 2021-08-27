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

import de.awagen.kolibri.base.io.json.ModifierMappersJsonProtocol._
import de.awagen.kolibri.base.processing.modifiers.ModifierMappers.{BodyMapper, HeadersMapper, ParamsMapper}
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import spray.json._


class ModifierMappersJsonProtocolSpec extends UnitTestSpec {

  val paramsMapperJson: JsValue =
    """
      |{
      |"replace": true,
      |"values": {
      | "type": "FROM_JSON_MAP",
      | "value": {
      |   "key1": {
      |     "test1": {"type": "BY_VALUES_SEQ", "values": [["0.10", "0.11"]]},
      |     "test2": {"type": "BY_VALUES_SEQ", "values": [["0.21", "0.22"]]}
      |   },
      |   "key2": {
      |     "test1": {"type": "BY_VALUES_SEQ", "values": [["0.4", "0.41"]]},
      |     "test2": {"type": "BY_VALUES_SEQ", "values": [["0.5", "0.55"]]}
      |   }
      | }
      |}
      |}
      |""".stripMargin.parseJson

  val headersMapperJson: JsValue =
    """
      |{
      |"replace": true,
      |"values": {
      | "type": "FROM_JSON_MAP",
      | "value": {
      |   "key1": {
      |     "hname1": {
      |       "type": "BY_VALUES_SEQ",
      |       "values": ["hvalue1", "hvalue3"]
      |     },
      |     "hname2": {
      |       "type": "BY_VALUES_SEQ",
      |       "values": ["hvalue2", "hvalue4"]
      |     }
      |   },
      |   "key2": {
      |     "hname1": {
      |       "type": "BY_VALUES_SEQ",
      |       "values": ["hvalue8", "hvalue10"]
      |     },
      |     "hname2": {
      |       "type": "BY_VALUES_SEQ",
      |       "values": ["hvalue9", "hvalue11"]
      |     }
      |   }
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


  "ModifierMappersJsonProtocol" must {

    "correctly parse ParamsMapper" in {
      // given, when
      val mapper = paramsMapperJson.convertTo[ParamsMapper]
      val value1: Map[String, Seq[Seq[String]]] = mapper.getValuesForKey("key1").get.view.mapValues(x => x.iterator.toSeq).toMap
      val value2: Map[String, Seq[Seq[String]]] = mapper.getValuesForKey("key2").get.view.mapValues(x => x.iterator.toSeq).toMap
      // then
      mapper.replace mustBe true
      mapper.keys.toSeq mustBe Seq("key1", "key2")
      value1 mustBe Map("test1" -> Seq(Seq("0.10", "0.11")), "test2" -> Seq(Seq("0.21", "0.22")))
      value2 mustBe Map("test1" -> Seq(Seq("0.4", "0.41")), "test2" -> Seq(Seq("0.5", "0.55")))
    }

    "correctly parse HeadersMapper" in {
      // given, when
      val mapper = headersMapperJson.convertTo[HeadersMapper]
      // then
      mapper.replace mustBe true
      mapper.keys.toSeq mustBe Seq("key1", "key2")
      val value1: Map[String, Seq[String]] = mapper.getValuesForKey("key1").get.view.mapValues(x => x.iterator.toSeq).toMap
      val value2: Map[String, Seq[String]] = mapper.getValuesForKey("key2").get.view.mapValues(x => x.iterator.toSeq).toMap
      value1 mustBe Map("hname1" -> Seq("hvalue1", "hvalue3"), "hname2" -> Seq("hvalue2", "hvalue4"))
      value2 mustBe Map("hname1" -> Seq("hvalue8", "hvalue10"), "hname2" -> Seq("hvalue9", "hvalue11"))
    }

    "correctly parse BodyMapper" in {
      // given, when
      val mapper = bodyMapperJson.convertTo[BodyMapper]
      // then
      mapper.keys.size mustBe 2
      val value1: Seq[String] = mapper.getValuesForKey("key1").get.iterator.toSeq
      val value2: Seq[String] = mapper.getValuesForKey("key2").get.iterator.toSeq
      value1 mustBe Seq("val1", "val2")
      value2 mustBe Seq("val3", "val4")
    }
  }

}
