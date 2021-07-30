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


package de.awagen.kolibri.base.usecase.searchopt.io.json

import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.base.usecase.searchopt.io.json.TypedJsonSelectorJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.parse.TypedJsonSelectors.TypedJsonSeqSelector
import play.api.libs.json.Json
import spray.json._

class TypedJsonSelectorJsonProtocolSpec extends UnitTestSpec {

  val stringSeqSelector: JsValue =
    """{
      |"type": "STRING",
      |"namedType": {
      | "name": "s1",
      | "type": "STRING"
      |},
      |"selector": {
      |"type": "SINGLEREC",
      |"path": "\\\\ id"
      |}
      |}
      |""".stripMargin.parseJson

  val doubleSeqSelector: JsValue =
    """{
      |"type": "DOUBLE",
      |"namedType": {
      | "name": "d1",
      | "type": "DOUBLE"
      |},
      |"selector": {
      |"type": "SINGLEREC",
      |"path": "\\\\ id"
      |}
      |}
      |""".stripMargin.parseJson

  val floatSeqSelector: JsValue =
    """{
      |"type": "FLOAT",
      |"namedType": {
      | "name": "f1",
      | "type": "FLOAT"
      |},
      |"selector": {
      |"type": "SINGLEREC",
      |"path": "\\\\ id"
      |}
      |}
      |""".stripMargin.parseJson

  val booleanSeqSelector: JsValue =
    """{
      |"type": "BOOLEAN",
      |"namedType": {
      | "name": "b1",
      | "type": "BOOLEAN"
      |},
      |"selector": {
      |"type": "SINGLEREC",
      |"path": "\\\\ id"
      |}
      |}
      |""".stripMargin.parseJson

  "TypedJsonSelectorJsonProtocol" must {

    "correctly parse TypedJsonSeqSelector of type String" in {
      val selector: TypedJsonSeqSelector[_] = stringSeqSelector.convertTo[TypedJsonSeqSelector[_]]
      val stringSeqParsed: collection.Seq[_] = selector.select(Json.parse("""[{"id": "1"}, {"id": "2"}, {"id": "3"}]"""))
      stringSeqParsed mustBe Seq("1", "2", "3")
      selector.namedClassTyped.name mustBe "s1"
    }

    "correctly parse TypedJsonSeqSelector of type Double" in {
      val selector: TypedJsonSeqSelector[_] = doubleSeqSelector.convertTo[TypedJsonSeqSelector[_]]
      val stringSeqParsed: collection.Seq[_] = selector.select(Json.parse("""[{"id": 1.1}, {"id": 2.2}, {"id": 3.3}]"""))
      stringSeqParsed mustBe Seq(1.1, 2.2, 3.3)
      selector.namedClassTyped.name mustBe "d1"
    }

    "correctly parse TypedJsonSeqSelector of type Float" in {
      val selector: TypedJsonSeqSelector[_] = floatSeqSelector.convertTo[TypedJsonSeqSelector[_]]
      val stringSeqParsed: collection.Seq[_] = selector.select(Json.parse("""[{"id": 1.1}, {"id": 2.2}, {"id": 3.3}]"""))
      stringSeqParsed mustBe Seq(1.1f, 2.2f, 3.3f)
      selector.namedClassTyped.name mustBe "f1"
    }

    "correctly parse TypedJsonSeqSelector of type Boolean" in {
      val selector: TypedJsonSeqSelector[_] = booleanSeqSelector.convertTo[TypedJsonSeqSelector[_]]
      val stringSeqParsed: collection.Seq[_] = selector.select(Json.parse("""[{"id": true}, {"id": false}, {"id": true}]"""))
      stringSeqParsed mustBe Seq(true, false, true)
      selector.namedClassTyped.name mustBe "b1"
    }
  }

}
