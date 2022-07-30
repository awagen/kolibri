/**
 * Copyright 2022 Andreas Wagenmann
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

import de.awagen.kolibri.datatypes.io.json.ClassTypedJsonProtocol.ClassTypedFormat
import de.awagen.kolibri.datatypes.io.json.ClassTypedJsonProtocol.TypeKeys._
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.types.ClassTyped
import spray.json.JsString

class ClassTypedJsonProtocolSpec extends UnitTestSpec {

  "ClassTypedJsonProtocol" must {

    "correctly parse type identifiers" in {
      JsString(STRING_TYPE).convertTo[ClassTyped[_]] mustBe ClassTyped[String]
      JsString(DOUBLE_TYPE).convertTo[ClassTyped[_]] mustBe ClassTyped[Double]
      JsString(FLOAT_TYPE).convertTo[ClassTyped[_]] mustBe ClassTyped[Float]
      JsString(BOOLEAN_TYPE).convertTo[ClassTyped[_]] mustBe ClassTyped[Boolean]
      JsString(SEQ_STRING_TYPE).convertTo[ClassTyped[_]] mustBe ClassTyped[Seq[String]]
      JsString(SEQ_DOUBLE_TYPE).convertTo[ClassTyped[_]] mustBe ClassTyped[Seq[Double]]
      JsString(SEQ_FLOAT_TYPE).convertTo[ClassTyped[_]] mustBe ClassTyped[Seq[Float]]
      JsString(SEQ_BOOLEAN_TYPE).convertTo[ClassTyped[_]] mustBe ClassTyped[Seq[Boolean]]
      JsString(MAP_STRING_DOUBLE).convertTo[ClassTyped[_]] mustBe ClassTyped[Map[String, Double]]
      JsString(MAP_STRING_FLOAT).convertTo[ClassTyped[_]] mustBe ClassTyped[Map[String, Float]]
      JsString(MAP_STRING_STRING).convertTo[ClassTyped[_]] mustBe ClassTyped[Map[String, String]]
      JsString(MAP_STRING_SEQ_STRING).convertTo[ClassTyped[_]] mustBe ClassTyped[Map[String, Seq[String]]]
    }

    "not erase type" in {
      JsString(SEQ_STRING_TYPE).convertTo[ClassTyped[_]] mustEqual ClassTyped[Seq[String]]
      JsString(SEQ_STRING_TYPE).convertTo[ClassTyped[_]] must not equal ClassTyped[Seq[Double]]
    }

  }


}
