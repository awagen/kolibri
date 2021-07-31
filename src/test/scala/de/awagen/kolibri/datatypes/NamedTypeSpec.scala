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


package de.awagen.kolibri.datatypes

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import play.api.libs.json.Json
import spray.json._


class NamedTypeSpec extends UnitTestSpec {

  val decimal: JsValue = """0.9""".parseJson
  val str: JsValue = JsString("0.9")
  val boolean: JsValue = """false""".parseJson

  val playDecimal: play.api.libs.json.JsValue = Json.parse("""0.9""")
  val playStr: play.api.libs.json.JsValue = play.api.libs.json.JsString("0.9")
  val playBoolean: play.api.libs.json.JsValue = Json.parse("""true""")

  "NamedType" must {

    "correctly cast spray types" in {
      NamedType.DOUBLE.cast(decimal) mustBe 0.9
      NamedType.FLOAT.cast(decimal) mustBe 0.9F
      NamedType.STRING.cast(str) mustBe "0.9"
      NamedType.BOOLEAN.cast(boolean) mustBe false
    }

    "correctly cast play types" in {
      NamedType.DOUBLE.cast(playDecimal) mustBe 0.9
      NamedType.FLOAT.cast(playDecimal) mustBe 0.9F
      NamedType.STRING.cast(playStr) mustBe "0.9"
      NamedType.BOOLEAN.cast(playBoolean) mustBe true
    }
  }

}
