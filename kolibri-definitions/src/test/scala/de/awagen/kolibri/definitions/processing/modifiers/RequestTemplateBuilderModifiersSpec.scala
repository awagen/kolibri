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

package de.awagen.kolibri.definitions.processing.modifiers

import de.awagen.kolibri.definitions.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers._
import de.awagen.kolibri.definitions.testclasses.UnitTestSpec

import scala.collection.immutable

class RequestTemplateBuilderModifiersSpec extends UnitTestSpec {

  def createRequestTemplateBuilder: RequestTemplateBuilder = new RequestTemplateBuilder()

  "Modifiers" must {

    "correctly adjust parameters" in {
      // given
      var builder_replace: RequestTemplateBuilder = createRequestTemplateBuilder
        .withParams(Map("k1" -> Seq("v3"), "k2" -> Seq("w2")))
      var builder_noreplace: RequestTemplateBuilder = createRequestTemplateBuilder
        .withParams(Map("k1" -> Seq("v3"), "k2" -> Seq("w2")))
      val modifier_replace: RequestParameterModifier = RequestParameterModifier(params = immutable.Map("k1" -> Seq("v1", "v2"), "k2" -> Seq("w1")), replace = true)
      val modifier_noreplace: RequestParameterModifier = RequestParameterModifier(params = immutable.Map("k1" -> Seq("v1", "v2"), "k2" -> Seq("w1")), replace = false)
      // when
      builder_replace = modifier_replace.apply(builder_replace)
      builder_noreplace = modifier_noreplace.apply(builder_noreplace)
      // then
      builder_replace.build().parameters mustBe Map("k1" -> Seq("v1", "v2"), "k2" -> Seq("w1"))
      builder_noreplace.build().parameters mustBe Map("k1" -> Seq("v3", "v1", "v2"), "k2" -> Seq("w2", "w1"))
    }

    "correctly apply ContextPathModifier" in {
      // given, when, then
      ContextPathModifier("newPath").apply(createRequestTemplateBuilder).build().contextPath mustBe "/newPath"
    }

    "correctly apply HeaderModifier" in {
      // given
      val builder_replace = createRequestTemplateBuilder.withHeaders(Seq(("h1", "v1"), ("h2", "v2")))
      val builder_noreplace = createRequestTemplateBuilder.withHeaders(Seq(("h1", "v1"), ("h2", "v2")))
      val modifier_replace = HeaderModifier(Seq(("h1", "v1"), ("h3", "v3")), replace = true)
      val modifier_noreplace = HeaderModifier(Seq(("h1", "v1"), ("h3", "v3")), replace = false)
      // when
      modifier_noreplace.apply(builder_noreplace)
      modifier_replace.apply(builder_replace)
      // then
      builder_noreplace.build().headers mustBe Seq(("h1", "v1"), ("h2", "v2"), ("h3", "v3"))
      builder_replace.build().headers mustBe Seq(("h1", "v1"), ("h3", "v3"))
    }

    "correctly apply BodyModifier" in {
      // given
      val testJsonBody = """{"key": "value"}"""
      val modifier = BodyModifier(testJsonBody)
      // when, then
      modifier.apply(createRequestTemplateBuilder).build().body mustBe testJsonBody
    }

    "correctly apply CombinedModifier" in {
      // given
      val testJsonBody = """{"key": "value"}"""
      val bodyModifier = BodyModifier(testJsonBody)
      val modifierReplace = HeaderModifier(Seq(("h1", "v1"), ("h3", "v3")), replace = true)
      val contextModifier = ContextPathModifier("newPath")
      // when
      val modifiedBuilder = CombinedModifier(Seq(bodyModifier, modifierReplace, contextModifier)).apply(createRequestTemplateBuilder)
      // then
      modifiedBuilder.build().headers mustBe Seq(("h1", "v1"), ("h3", "v3"))
      modifiedBuilder.build().body mustBe testJsonBody
      modifiedBuilder.build().contextPath mustBe "/newPath"
    }

    "correctly apply BodyReplaceModifier on build irrespective of order of application of body and bodyReplace modifiers" in {
      // given
      val testJsonBodyInit = """{"key": "value", "key2": "$$value2$$"}"""
      val testJsonBodyFinal = """{"key": "value", "key2": "yay"}"""
      val modifier = BodyModifier(testJsonBodyInit)
      val modifier1 = BodyReplaceModifier(Map("$$value2$$" -> "yay"))
      // when, then
      val modifiedTemplateBuilder1 = modifier1.apply(modifier.apply(createRequestTemplateBuilder))
      val modifiedTemplateBuilder2 = modifier.apply(modifier1.apply(createRequestTemplateBuilder))
      modifiedTemplateBuilder1.build().body mustBe testJsonBodyFinal
      modifiedTemplateBuilder2.build().body mustBe testJsonBodyFinal
    }

    "correctly apply UrlParameterReplaceModifier" in {
      // given
      val templateBuilder = createRequestTemplateBuilder
      templateBuilder.withParams(Map("p1" -> Seq("$$replace1", "$$replace2"), "p2" -> Seq("$$replace3")))
      val urlParamReplaceModifier = UrlParameterReplaceModifier(Map(
        "$$replace1" -> "val1",
        "$$replace2" -> "val2",
        "$$replace3" -> "val3"
      ))
      // when
      val template: RequestTemplate = urlParamReplaceModifier.apply(templateBuilder).build()
      // then
      template.parameters mustBe Map("p1" -> Seq("val1", "val2"), "p2" -> Seq("val3"))
    }

    "correctly apply HeaderValueReplaceModifier" in {
      // given
      val templateBuilder = createRequestTemplateBuilder
      val nestedHeaderValue = "{\"k1\": \"b\", \"k2\": \"$$replace2\"}"
      val replacedNestedHeaderValue = "{\"k1\": \"b\", \"k2\": \"v2\"}"
      templateBuilder.withHeaders(Seq(("h1", "$replace1"), ("h2", nestedHeaderValue)))
      val headerValueReplaceModifier = HeaderValueReplaceModifier(
        Map("$replace1" -> "v1", "$$replace2" -> "v2")
      )
      // when
      val template: RequestTemplate = headerValueReplaceModifier.apply(templateBuilder).build()
      // then
      template.getHeader("h1").get._2 mustBe "v1"
      template.getHeader("h2").get._2 mustBe replacedNestedHeaderValue
    }

  }

}
