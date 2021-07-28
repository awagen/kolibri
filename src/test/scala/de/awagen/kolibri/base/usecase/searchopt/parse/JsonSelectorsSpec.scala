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


package de.awagen.kolibri.base.usecase.searchopt.parse

import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors.{PlainAndRecursiveSelector, PlainPathSelector, RecursiveSelector, Selector, SingleKeySelector, findPlainPathKeys, findRecursivePathKeys, pathToPlainSelector, plainPathKeyGroupingRegex, recursivePathKeyGroupingRegex}
import play.api.libs.json.{DefaultReads, JsDefined, JsLookupResult, JsValue, Json}


class JsonSelectorsSpec extends UnitTestSpec with DefaultReads {

  "JsonSelectors" must {
    val json: JsValue = Json.parse(
      """{
        |"key1": "v1",
        |"key2": "v2",
        |"key3": [{"nkey1": "nv1"}, {"nkey1": "nv2"}, {"nkey1": "nv3"}],
        |"key4": {"key4_1": {"key4_1_1": "vv1"}},
        |"key5": {"key5_1": [{"key5_r": "vv1"}, {"key5_r": "vv2"}, {"key5_r": "vv3"}]}
        |}
        |""".stripMargin)

    val jsonArray: JsValue = Json.parse(
      """
        |[{"nkey1": "nv1"}, {"nkey1": "nv2"}, {"nkey1": "nv3"}]
        |""".stripMargin
    )

    "correctly apply SingleKeySelector on JsValue" in {
      // given
      val selector = SingleKeySelector("key2")
      // when, then
      selector.select(json).get.as[String] mustBe "v2"
    }

    "correctly apply SingleKeySelector on LookupResult" in {
      // given
      val selector = SingleKeySelector("key1")
      // when, then
      selector.select(JsDefined(json)).get.as[String] mustBe "v1"
    }

    "correctly apply RecursiveSelector" in {
      // given
      val selector = RecursiveSelector("nkey1")
      // when
      val result = selector.select(jsonArray).map(x => x.as[String]).toSeq
      // then
      result mustBe Seq("nv1", "nv2", "nv3")
    }

    "correctly apply PlainPathSelector" in {
      // given
      val selector = PlainPathSelector(Seq("key4", "key4_1", "key4_1_1"))
      // when
      selector.select(json).as[String] mustBe "vv1"
    }

    "correctly apply PlainAndRecursiveSelector" in {
      // given
      val selector = PlainAndRecursiveSelector("key5_r", Seq("key5", "key5_1"): _*)
      // when
      selector.select(json).map(x => x.as[String]).toSeq mustBe Seq("vv1", "vv2", "vv3")
    }

    "correctly apply regex for valid path check" in {
      plainPathKeyGroupingRegex.matches("\\") mustBe false
      plainPathKeyGroupingRegex.matches("\\ word") mustBe true
      plainPathKeyGroupingRegex.matches("\\ word\\") mustBe false
      plainPathKeyGroupingRegex.matches("\\ word \\") mustBe false
      plainPathKeyGroupingRegex.matches("\\ word \\ aaa") mustBe true
    }

    "correctly match the single words" in {
      val subgroups1 = findPlainPathKeys("\\ word \\ aaa")
      val subgroups2 = findPlainPathKeys("\\ word \\ aaa \\ bbb")
      subgroups1 mustBe Seq("word", "aaa")
      subgroups2 mustBe Seq("word", "aaa", "bbb")
    }

    "correctly match recursive selector keys" in {
      val subgroups1 = findRecursivePathKeys("\\ word \\ aaa \\\\ bbb")
      val subgroups2 = findRecursivePathKeys("\\ word \\ aaa \\ bbb \\\\ ccc")
      subgroups1 mustBe Seq("word", "aaa", "bbb")
      subgroups2 mustBe Seq("word", "aaa", "bbb", "ccc")
    }

    "correctly apply pathToPlainSelector" in {
      val selector: Selector[JsLookupResult] = pathToPlainSelector("\\ word \\ aaa \\ bbb")
      val value: JsLookupResult = selector.select(
        Json.parse("""{"word": {"aaa": {"bbb": "value"}}}""".stripMargin)
      )
      value.as[String] mustBe "value"
    }

  }

}
