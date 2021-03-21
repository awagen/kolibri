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

package de.awagen.kolibri.datatypes.tagging

import de.awagen.kolibri.datatypes.tagging.Tags.{AggregationTag, MultiTag, ParameterMultiValueTag, ParameterSingleValueTag, ParameterTag, StringTag, Tag}
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec


class TagsSpec extends UnitTestSpec {

  val tag1: Tag = StringTag("tag1")
  val multiParamMap1: Map[String, Seq[String]] = Map("p1" -> Seq("v1"), "p2" -> Seq("v2", "v3"))
  val singleParamMap1: Map[String, String] = Map("p1" -> "v1", "p2" -> "v2")
  val singleParamMap2: Map[String, String] = Map("t1" -> "t2", "u1" -> "u2")
  val multiParamMap2: Map[String, Seq[String]] = Map("p1" -> Seq("v1"), "p2" -> Seq("v2", "v3"), "n1" -> Seq("v4", "v5"))
  val tag2: Tag = ParameterMultiValueTag(value = multiParamMap1)
  val tag3: Tag = ParameterSingleValueTag(value = singleParamMap1)
  val tag4: Tag = MultiTag(Set(ParameterSingleValueTag(value = singleParamMap1), StringTag("t1")))
  val tag5: Tag = ParameterMultiValueTag(value = multiParamMap2)

  "Tags" must {

    "generate tagToParameterHeader" in {
      // given, when
      val header1 = Tags.tagToParameterHeader(tag1, "\t")
      val header2 = Tags.tagToParameterHeader(tag2, "\t")
      val header3 = Tags.tagToParameterHeader(tag3, "\t")
      val header4 = Tags.tagToParameterHeader(tag4, "\t")
      val header5 = Tags.tagToParameterHeader(tag5, "\t")
      // then
      header1 mustBe ""
      header2 mustBe "p1\tp2"
      header3 mustBe "p1\tp2"
      header4 mustBe "p1\tp2"
      header5 mustBe "n1\tp1\tp2"
    }

    "generate tagToParameterValues" in {
      // given, when
      val header1 = Tags.tagToParameterValues(tag1, "\t")
      val header2 = Tags.tagToParameterValues(tag2, "\t")
      val header3 = Tags.tagToParameterValues(tag3, "\t")
      val header4 = Tags.tagToParameterValues(tag4, "\t")
      val header5 = Tags.tagToParameterValues(tag5, "\t")
      // then
      header1 mustBe ""
      header2 mustBe "v1\tv2//v3"
      header3 mustBe "v1\tv2"
      header4 mustBe "v1\tv2"
      header5 mustBe "v4//v5\tv1\tv2//v3"
    }

  }

  "ParameterMultiValueTag" must {

    "correctly format param header" in {
      // given
      val tag1 = ParameterMultiValueTag(value = multiParamMap1)
      val tag2 = ParameterMultiValueTag(value = multiParamMap2)
      // when
      val header1 = tag1.formattedParamHeader(",")
      val header2 = tag2.formattedParamHeader(",")
      // then
      header1 mustBe "p1,p2"
      header2 mustBe "n1,p1,p2"
    }

    "correctly format param values" in {
      // given
      val tag1 = ParameterMultiValueTag(value = multiParamMap1)
      val tag2 = ParameterMultiValueTag(value = multiParamMap2)
      // when
      val values1 = tag1.formattedParamValues(",")
      val values2 = tag2.formattedParamValues(",")
      // then
      values1 mustBe "v1,v2//v3"
      values2 mustBe "v4//v5,v1,v2//v3"
    }

    "only generate one map key for same values" in {
      // given
      val parameterMap1: Map[String, Seq[String]] = Map("p1" -> Seq("v1"), "p2" -> Seq("v2", "v3"))
      val parameterMap2: Map[String, Seq[String]] = Map("p1" -> Seq("v1"), "p2" -> Seq("v2", "v3"))
      val parameterMap3: Map[String, Seq[String]] = Map("s1" -> Seq("t1"), "s2" -> Seq("t2", "t3"))
      val tag1: Tag = ParameterMultiValueTag(parameterMap1)
      val tag2: Tag = ParameterMultiValueTag(parameterMap2)
      val tag3: Tag = ParameterMultiValueTag(parameterMap3)
      var map: Map[Tag, String] = Map.empty
      // when
      map = map + (tag1 -> "hey1")
      map = map + (tag2 -> "hey2")
      map = map + (tag3 -> "hey3")
      // then
      map.keySet.size mustBe 2
      map(tag1) mustBe "hey2"
      map(tag2) mustBe "hey2"
      map(tag3) mustBe "hey3"
    }
  }

  "ParameterSingleValueTag" must {

    "correctly format param header" in {
      // given
      val tag1 = ParameterSingleValueTag(value = Map[String, String]("k1" -> "v1", "k2" -> "v2"))
      val tag2 = ParameterSingleValueTag(value = Map[String, String]("k3" -> "v3", "b1" -> "b2", "a1" -> "a2"))
      // when
      val header1 = tag1.formattedParamHeader(",")
      val header2 = tag2.formattedParamHeader(",")
      // then
      header1 mustBe "k1,k2"
      header2 mustBe "a1,b1,k3"
    }

    "correctly format param values" in {
      val tag1 = ParameterSingleValueTag(value = Map[String, String]("k1" -> "v1", "k2" -> "v2"))
      val tag2 = ParameterSingleValueTag(value = Map[String, String]("k3" -> "v3", "b1" -> "b2", "a1" -> "a2"))
      // when
      val header1 = tag1.formattedParamValues(",")
      val header2 = tag2.formattedParamValues(",")
      // then
      header1 mustBe "v1,v2"
      header2 mustBe "a2,b2,v3"
    }

    "only generate one map key for same values" in {
      // given
      val tag1: Tag = ParameterSingleValueTag(Map[String, String]("b1" -> "b2", "a1" -> "a2"))
      val tag2: Tag = ParameterSingleValueTag(Map[String, String]("a1" -> "a2", "b1" -> "b2"))
      val tag3: Tag = ParameterSingleValueTag(Map[String, String]("a1" -> "a2", "b1" -> "b2", "c1" -> "c2"))
      var map: Map[Tag, String] = Map.empty
      // when
      map = map + (tag1 -> "hey1")
      map = map + (tag2 -> "hey2")
      map = map + (tag3 -> "hey3")
      // then
      map.keySet.size mustBe 2
      map(tag1) mustBe "hey2"
      map(tag2) mustBe "hey2"
      map(tag3) mustBe "hey3"
    }
  }

  "MultiTag" must {

    "keep track of all added tags and ignore duplicates" in {
      // given, when
      var tag = MultiTag(Set.empty)
      tag = tag.add(StringTag("t1"))
      tag = tag.add(StringTag("t1"))
      tag = tag.add(StringTag("t2"))
      // then
      tag.value.size mustBe 2
      tag.value.contains(StringTag("t1")) mustBe true
      tag.value.contains(StringTag("t2")) mustBe true
    }

    "generate single map key for same contained tags" in {
      // given
      val tag1 = MultiTag(Set.empty).add(StringTag("t1"))
        .add(StringTag("t2"))
      val tag1_1 = MultiTag(Set.empty).add(StringTag("t1"))
        .add(StringTag("t2"))
      val tag2 = MultiTag(Set.empty).add(StringTag("v1"))
        .add(StringTag("v2"))
      var map: Map[Tag, String] = Map.empty
      // when
      map = map + (tag1 -> "hey1")
      map = map + (tag1_1 -> "hey2")
      map = map + (tag2 -> "hey3")
      // then
      map.keySet.size mustBe 2
      map(tag1) mustBe "hey2"
      map(tag2) mustBe "hey3"
    }
  }

  "AggregationTag" must {

    val sParamTag1: ParameterTag = ParameterSingleValueTag(singleParamMap1)
    val sParamTag2: ParameterTag = ParameterSingleValueTag(singleParamMap2)
    val mParamTag1: ParameterTag = ParameterMultiValueTag(value = multiParamMap1)
    val mParamTag2: ParameterTag = ParameterMultiValueTag(value = multiParamMap2)
    val tag1 = AggregationTag("id1", sParamTag1, mParamTag1)
    val tag1_1 = AggregationTag("id1", sParamTag1, mParamTag1)
    val tag2 = AggregationTag("id1", sParamTag2, mParamTag2)

    "equal for same values" in {
      (tag1 == tag1_1) mustBe true
      (tag1 == tag2) mustBe false
    }

    "generate single map key for same contained tags" in {
      // given, when
      var map: Map[Tag, String] = Map.empty
      map = map + (tag1 -> "yay1")
      map = map + (tag1_1 -> "yay2")
      map = map + (tag2 -> "yay3")
      // then
      map.keySet.size mustBe 2
      map(tag1) mustBe "yay2"
      map(tag2) mustBe "yay3"
    }

  }

  "StringTag" must {

    val tag1 = StringTag("v1")
    val tag2 = StringTag("v1")
    val tag3 = StringTag("v2")

    "equal for same values" in {
      (tag1 == tag2) mustBe true
      (tag1 == tag3) mustBe false
    }

    "overwrite key value in map if same value" in {
      // given, when
      var map: Map[Tag, String] = Map.empty
      map = map + (tag1 -> "y1")
      map = map + (tag2 -> "y2")
      map = map + (tag3 -> "y3")
      // then
      map.keySet.size mustBe 2
      map(tag1) mustBe "y2"
      map(tag3) mustBe "y3"
    }

  }

}
