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

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.util.ByteString
import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers._
import de.awagen.kolibri.base.testclasses.UnitTestSpec

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
      val builder_replace = createRequestTemplateBuilder.withHeaders(Seq(RawHeader("h1", "v1"), RawHeader("h2", "v2")))
      val builder_noreplace = createRequestTemplateBuilder.withHeaders(Seq(RawHeader("h1", "v1"), RawHeader("h2", "v2")))
      val modifier_replace = HeaderModifier(Seq(RawHeader("h1", "v1"), RawHeader("h3", "v3")), replace = true)
      val modifier_noreplace = HeaderModifier(Seq(RawHeader("h1", "v1"), RawHeader("h3", "v3")), replace = false)
      // when
      modifier_noreplace.apply(builder_noreplace)
      modifier_replace.apply(builder_replace)
      // then
      builder_noreplace.build().headers mustBe Seq(RawHeader("h1", "v1"), RawHeader("h2", "v2"), RawHeader("h3", "v3"))
      builder_replace.build().headers mustBe Seq(RawHeader("h1", "v1"), RawHeader("h3", "v3"))
    }

    "correctly apply BodyModifier" in {
      // given
      val testJsonBody = """{"key": "value"}"""
      val body = HttpEntity.Strict(ContentTypes.`application/json`, ByteString(testJsonBody))
      val modifier = BodyModifier(body)
      // when, then
      modifier.apply(createRequestTemplateBuilder).build().body mustBe body
    }

    "correctly apply CombinedModifier" in {
      // given
      val testJsonBody = """{"key": "value"}"""
      val body = HttpEntity.Strict(ContentTypes.`application/json`, ByteString(testJsonBody))
      val bodyModifier = BodyModifier(body)
      val modifierReplace = HeaderModifier(Seq(RawHeader("h1", "v1"), RawHeader("h3", "v3")), replace = true)
      val contextModifier = ContextPathModifier("newPath")
      // when
      val modifiedBuilder = CombinedModifier(Seq(bodyModifier, modifierReplace, contextModifier)).apply(createRequestTemplateBuilder)
      // then
      modifiedBuilder.build().headers mustBe Seq(RawHeader("h1", "v1"), RawHeader("h3", "v3"))
      modifiedBuilder.build().body mustBe body
      modifiedBuilder.build().contextPath mustBe "/newPath"
    }

  }

}
