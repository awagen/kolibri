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
import de.awagen.kolibri.base.usecase.searchopt.io.json.NamedClassTypedKeyJsonProtocol._
import de.awagen.kolibri.datatypes.NamedClassTyped
import spray.json._

class NamedClassTypedKeyJsonProtocolSpec extends UnitTestSpec {

  val stringKey = """{"name": "v1", "type": "STRING"}"""
  val stringValue: NamedClassTyped[String] = NamedClassTyped[String]("v1")
  val doubleKey = """{"name": "v2", "type": "DOUBLE"}"""
  val doubleValue: NamedClassTyped[Double] = NamedClassTyped[Double]("v2")
  val floatKey = """{"name": "v3", "type": "FLOAT"}"""
  val floatValue: NamedClassTyped[Float] = NamedClassTyped[Float]("v3")
  val seqFloatKey = """{"name": "v4", "type": "SEQ[FLOAT]"}"""
  val seqFloatValue: NamedClassTyped[Seq[Float]] = NamedClassTyped[Seq[Float]]("v4")
  val seqStringKey = """{"name": "v5", "type": "SEQ[STRING]"}"""
  val seqStringValue: NamedClassTyped[Seq[String]] = NamedClassTyped[Seq[String]]("v5")
  val seqDoubleKey = """{"name": "v6", "type": "SEQ[DOUBLE]"}"""
  val seqDoubleValue: NamedClassTyped[Seq[Double]] = NamedClassTyped[Seq[Double]]("v6")


  "NamedClassTypedKeyJsonProtocolSpec" must {

    "correctly parse NamedClassTyped" in {
      stringKey.parseJson.convertTo[NamedClassTyped[_]] == stringValue mustBe true
      doubleKey.parseJson.convertTo[NamedClassTyped[_]] == doubleValue mustBe true
      floatKey.parseJson.convertTo[NamedClassTyped[_]] == floatValue mustBe true
      seqFloatKey.parseJson.convertTo[NamedClassTyped[_]] == seqFloatValue mustBe true
      seqStringKey.parseJson.convertTo[NamedClassTyped[_]] == seqStringValue mustBe true
      seqDoubleKey.parseJson.convertTo[NamedClassTyped[_]] == seqDoubleValue mustBe true
    }

    "correctly write NamesClassTyped" in {
      NamedClassTypedKeyFormat.write(stringValue) mustBe stringKey.toJson
      NamedClassTypedKeyFormat.write(doubleValue) mustBe doubleKey.toJson
      NamedClassTypedKeyFormat.write(floatValue) mustBe floatKey.toJson
      NamedClassTypedKeyFormat.write(seqFloatValue) mustBe seqFloatKey.toJson
      NamedClassTypedKeyFormat.write(seqStringValue) mustBe seqStringKey.toJson
      NamedClassTypedKeyFormat.write(seqDoubleValue) mustBe seqDoubleKey.toJson
    }

  }

}
