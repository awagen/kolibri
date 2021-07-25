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
import JsonSelectorJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors.RecursiveValueSelector
import play.api.libs.json
import play.api.libs.json.Json
import spray.json._


class JsonSelectorJsonProtocolSpec extends UnitTestSpec {

  "JsonSelectorJsonProtocol" must {

    "correctly parse Seq[String] selector" in {
      // given
      val toBeParsed: json.JsValue = Json.parse(
        """{
          |"data": {"products": [{"p_id": "pid1"}, {"p_id": "pid2"}, {"p_id": "pid3"}]}
          |}""".stripMargin)
      val selectorJson =
        """{"plainSelectorKeys": ["data", "products"],
          |"recursiveSelectorKey": "p_id"
          |}""".stripMargin
      // when
      val selector: RecursiveValueSelector[String] = selectorJson.parseJson.convertTo[RecursiveValueSelector[String]]
      val selection: Seq[String] = selector.select(toBeParsed)
      // then
      selection mustBe Seq("pid1", "pid2", "pid3")
    }

  }


}
