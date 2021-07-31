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


package de.awagen.kolibri.datatypes.io.json

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import spray.json.{JsString, JsValue}
import EnumerationJsonProtocol._
import de.awagen.kolibri.datatypes.NamedType
import de.awagen.kolibri.datatypes.NamedType.NamedType

class EnumerationJsonProtocolSpec extends UnitTestSpec {

  val string: JsValue = JsString("STRING")
  val double: JsValue = JsString("DOUBLE")
  val float: JsValue = JsString("FLOAT")
  val boolean: JsValue = JsString("BOOLEAN")

  val seqString: JsValue = JsString("SEQ_STRING")
  val seqDouble: JsValue = JsString("SEQ_DOUBLE")
  val seqFloat: JsValue = JsString("SEQ_FLOAT")
  val seqBoolean: JsValue = JsString("SEQ_BOOLEAN")

  "EnumerationJsonProtocol" must {

    "correctly parse NamedType" in {
      string.convertTo[NamedType] mustBe NamedType.STRING
      double.convertTo[NamedType] mustBe NamedType.DOUBLE
      float.convertTo[NamedType] mustBe NamedType.FLOAT
      boolean.convertTo[NamedType] mustBe NamedType.BOOLEAN
      seqString.convertTo[NamedType] mustBe NamedType.SEQ_STRING
      seqDouble.convertTo[NamedType] mustBe NamedType.SEQ_DOUBLE
      seqFloat.convertTo[NamedType] mustBe NamedType.SEQ_FLOAT
      seqBoolean.convertTo[NamedType] mustBe NamedType.SEQ_BOOLEAN
    }
  }

}
