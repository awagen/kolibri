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
import de.awagen.kolibri.datatypes.JsonTypeCast
import play.api.libs.json.Json
import spray.json._

class TypedJsonSelectorJsonProtocolSpec extends UnitTestSpec {

  val stringSeqSelector: JsValue =
    """{
      |"name": "string1",
      |"castType": "STRING",
      |"selector": {
      |"type": "SINGLEREC",
      |"path": "\\\\ id"
      |}
      |}
      |""".stripMargin.parseJson

  val doubleSeqSelector: JsValue =
    """{
      |"name": "double1",
      |"castType": "DOUBLE",
      |"selector": {
      |"type": "SINGLEREC",
      |"path": "\\\\ id"
      |}
      |}
      |""".stripMargin.parseJson

  val floatSeqSelector: JsValue =
    """{
      |"name": "float1",
      |"castType": "FLOAT",
      |"selector": {
      |"type": "SINGLEREC",
      |"path": "\\\\ id"
      |}
      |}
      |""".stripMargin.parseJson

  val booleanSeqSelector: JsValue =
    """{
      |"name": "boolean1",
      |"castType": "BOOLEAN",
      |"selector": {
      |"type": "SINGLEREC",
      |"path": "\\\\ id"
      |}
      |}
      |""".stripMargin.parseJson

  "TypedJsonSelectorJsonProtocol" must {

    "correctly parse TypedJsonSeqSelector of type String" in {
      val selector: TypedJsonSeqSelector = stringSeqSelector.convertTo[TypedJsonSeqSelector]
      val stringSeqParsed: collection.Seq[_] = selector.select(Json.parse("""[{"id": "1"}, {"id": "2"}, {"id": "3"}]"""))
      stringSeqParsed mustBe Seq("1", "2", "3")
      selector.castType mustBe JsonTypeCast.STRING
      selector.name mustBe "string1"
    }

    "correctly parse TypedJsonSeqSelector of type Double" in {
      val selector: TypedJsonSeqSelector = doubleSeqSelector.convertTo[TypedJsonSeqSelector]
      val stringSeqParsed: collection.Seq[_] = selector.select(Json.parse("""[{"id": 1.1}, {"id": 2.2}, {"id": 3.3}]"""))
      stringSeqParsed mustBe Seq(1.1, 2.2, 3.3)
      selector.castType mustBe JsonTypeCast.DOUBLE
      selector.name mustBe "double1"
    }

    "correctly parse TypedJsonSeqSelector of type Float" in {
      val selector: TypedJsonSeqSelector = floatSeqSelector.convertTo[TypedJsonSeqSelector]
      val stringSeqParsed: collection.Seq[_] = selector.select(Json.parse("""[{"id": 1.1}, {"id": 2.2}, {"id": 3.3}]"""))
      stringSeqParsed mustBe Seq(1.1f, 2.2f, 3.3f)
      selector.castType mustBe JsonTypeCast.FLOAT
      selector.name mustBe "float1"
    }

    "correctly parse TypedJsonSeqSelector of type Boolean" in {
      val selector: TypedJsonSeqSelector = booleanSeqSelector.convertTo[TypedJsonSeqSelector]
      val stringSeqParsed: collection.Seq[_] = selector.select(Json.parse("""[{"id": true}, {"id": false}, {"id": true}]"""))
      stringSeqParsed mustBe Seq(true, false, true)
      selector.castType mustBe JsonTypeCast.BOOLEAN
      selector.name mustBe "boolean1"
    }
  }

}
