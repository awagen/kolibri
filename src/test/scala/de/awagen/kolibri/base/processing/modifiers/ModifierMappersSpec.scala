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

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.util.ByteString
import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.base.processing.modifiers.ModifierMappers._
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}

class ModifierMappersSpec extends UnitTestSpec {

  val paramsGenerator1: Map[String, IndexedGenerator[Seq[String]]] = {
    Map("p1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(Seq("v1_1", "v1_2"))),
      "p2" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(Seq("v2_1", "v2_2"))),
      "p3" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(Seq("v3_1", "v3_2", "v3_3")))
    )
  }

  val paramsGenerator2: Map[String, IndexedGenerator[Seq[String]]] =
    Map("v1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(Seq("k1_1", "k1_2"))),
      "v2" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(Seq("k2_1", "k2_2"))),
      "v3" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(Seq("k3_1")))
    )

  val headersGenerator1: Map[String, IndexedGenerator[String]] = {
    Map(
      "header1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("headerValue1_1")),
      "header2" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("headerValue1_2")),
      "header3" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("headerValue1_3"))
    )
  }

  val headersGenerator2: Map[String, IndexedGenerator[String]] =
    Map(
      "header1" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("headerValue2_1")),
      "header2" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("headerValue2_2")),
      "header3" -> ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("headerValue2_3"))
    )

  val bodiesGenerator1: IndexedGenerator[String] = ByFunctionNrLimitedIndexedGenerator.createFromSeq(
    Seq(
      """{"name": "body1"}""",
      """{"name": "body2"}""",
      """{"name": "body3"}""",
    )
  )
  val bodiesGenerator2: IndexedGenerator[String] = ByFunctionNrLimitedIndexedGenerator.createFromSeq(
    Seq(
      """{"name": "yay1"}""",
      """{"name": "yay2"}""",
      """{"name": "yay3"}""",
    )
  )

  def emptyRequestTemplateBuilder: RequestTemplateBuilder = new RequestTemplateBuilder()

  def applyModifiersOnEmptyTemplateBuilderAndReturn(modifierMapper: ModifierMapper[_]): Seq[RequestTemplate] = {
    val keys = modifierMapper.keys
    keys.map(key => {
      var templateBuilder = emptyRequestTemplateBuilder
      modifierMapper.getModifiersForKey(key).get.iterator.foreach(mod => {
        templateBuilder = mod.apply(templateBuilder)
      })
      templateBuilder.build()
    }).toSeq
  }

  val paramsMapper: ParamsMapper = BaseParamsMapper(Map("key1" -> paramsGenerator1, "key2" -> paramsGenerator2), replace = false)
  val headersMapper: HeadersMapper = BaseHeadersMapper(Map("key1" -> headersGenerator1, "key2" -> headersGenerator2), replace = false)
  val bodyMapper: BodyMapper = BaseBodyMapper(Map("key1" -> bodiesGenerator1, "key2" -> bodiesGenerator2))


  "ModifierMappers" must {

    "correctly apply ParamsMapper" in {
      // given, when
      val templates = applyModifiersOnEmptyTemplateBuilderAndReturn(paramsMapper)
      // then
      templates.head.getParameter("p1").get mustBe Seq("v1_1", "v1_2")
      templates.head.getParameter("p2").get mustBe Seq("v2_1", "v2_2")
      templates.head.getParameter("p3").get mustBe Seq("v3_1", "v3_2", "v3_3")
      templates(1).getParameter("v1").get mustBe Seq("k1_1", "k1_2")
      templates(1).getParameter("v2").get mustBe Seq("k2_1", "k2_2")
      templates(1).getParameter("v3").get mustBe Seq("k3_1")

    }

    "correctly apply HeadersMapper" in {
      // given, when
      val templates: Seq[RequestTemplate] = applyModifiersOnEmptyTemplateBuilderAndReturn(headersMapper)
      // then
      templates.head.headers.find(header => header.name() == "header1").get.value() mustBe "headerValue1_1"
      templates.head.headers.find(header => header.name() == "header2").get.value() mustBe "headerValue1_2"
      templates.head.headers.find(header => header.name() == "header3").get.value() mustBe "headerValue1_3"
      templates(1).headers.find(header => header.name() == "header1").get.value() mustBe "headerValue2_1"
      templates(1).headers.find(header => header.name() == "header2").get.value() mustBe "headerValue2_2"
      templates(1).headers.find(header => header.name() == "header3").get.value() mustBe "headerValue2_3"
    }

    "correctly apply BodiesMapper" in {
      // given, when
      val key1Templates: IndexedGenerator[Modifier[RequestTemplateBuilder]] = bodyMapper.getModifiersForKey("key1").get
      val key2Templates: IndexedGenerator[Modifier[RequestTemplateBuilder]] = bodyMapper.getModifiersForKey("key2").get
      val appliedModifiers1: Seq[RequestTemplate] = key1Templates.mapGen(modifier => modifier.apply(emptyRequestTemplateBuilder))
        .iterator.map(x => x.build()).toSeq
      val appliedModifiers2: Seq[RequestTemplate] = key2Templates.mapGen(modifier => modifier.apply(emptyRequestTemplateBuilder))
        .iterator.map(x => x.build()).toSeq
      // then
      appliedModifiers1.map(x => x.body) mustBe Seq(
        HttpEntity.Strict(ContentTypes.`application/json`, ByteString("""{"name": "body1"}""")),
        HttpEntity.Strict(ContentTypes.`application/json`, ByteString("""{"name": "body2"}""")),
        HttpEntity.Strict(ContentTypes.`application/json`, ByteString("""{"name": "body3"}"""))
      )
      appliedModifiers2.map(x => x.body) mustBe Seq(
        HttpEntity.Strict(ContentTypes.`application/json`, ByteString("""{"name": "yay1"}""")),
        HttpEntity.Strict(ContentTypes.`application/json`, ByteString("""{"name": "yay2"}""")),
        HttpEntity.Strict(ContentTypes.`application/json`, ByteString("""{"name": "yay3"}"""))
      )
    }

  }
}
