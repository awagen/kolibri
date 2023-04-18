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


package de.awagen.kolibri.base.processing.modifiers

import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.base.processing.modifiers.ModifierMappers.{BaseBodyMapper, BaseHeadersMapper, BaseParamsMapper}
import de.awagen.kolibri.base.processing.modifiers.RequestPermutations.MappingModifier
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator

class RequestPermutationsSpec extends UnitTestSpec {

  def emptyRequestTemplateBuilder: RequestTemplateBuilder = new RequestTemplateBuilder()

  "RequestPermutations" must {

    "correctly apply MappingModifier" in {
      // given, when
      val mappingModifier = MappingModifier(
        keyGen = ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("key1", "key2")),
        paramsMapper = BaseParamsMapper(Map("key1" -> Map("p1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(Seq("v1")))),
          "key2" -> Map("pp1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(Seq("vv1"))))), replace = false),
        headersMapper = BaseHeadersMapper(Map("key1" -> Map("p1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("v1"))),
          "key2" -> Map("pp1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("vv1")))), replace = false),
        bodyMapper = BaseBodyMapper(Map("key1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("""{"a": "A"}""")),
          "key2" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("""{"b": "B"}"""))))
      )
      mappingModifier.modifiers.size mustBe 1
      val results: Seq[RequestTemplate] = mappingModifier.modifiers.head.mapGen(modifier => modifier.apply(emptyRequestTemplateBuilder).build()).iterator.toSeq
      results.size mustBe 2
      results.head.getParameter("p1").get mustBe Seq("v1")
      results(1).getParameter("pp1").get mustBe Seq("vv1")
      results.head.headers.find(x => x._1 == "p1").get._2 mustBe "v1"
      results(1).headers.find(x => x._1 == "pp1").get._2 mustBe "vv1"
      results.head.body mustBe """{"a": "A"}"""
      results(1).body mustBe """{"b": "B"}"""
    }
  }

}
