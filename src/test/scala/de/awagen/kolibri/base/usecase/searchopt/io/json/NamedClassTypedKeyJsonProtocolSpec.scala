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
  val doubleKey = """{"name": "v2", "type": "DOUBLE"}"""
  val floatKey = """{"name": "v3", "type": "FLOAT"}"""
  val seqFloatKey = """{"name": "v4", "type": "SEQ[FLOAT]"}"""
  val seqStringKey = """{"name": "v5", "type": "SEQ[STRING]"}"""
  val seqDoubleKey = """{"name": "v6", "type": "SEQ[DOUBLE]"}"""

  "NamedClassTypedKeyJsonProtocolSpec" must {

    "correctly parse NamedClassTyped" in {
      stringKey.parseJson.convertTo[NamedClassTyped[_]] == NamedClassTyped[String]("v1") mustBe true
      doubleKey.parseJson.convertTo[NamedClassTyped[_]] == NamedClassTyped[Double]("v2") mustBe true
      floatKey.parseJson.convertTo[NamedClassTyped[_]] == NamedClassTyped[Float]("v3") mustBe true
      seqFloatKey.parseJson.convertTo[NamedClassTyped[_]] == NamedClassTyped[Seq[Float]]("v4") mustBe true
      seqStringKey.parseJson.convertTo[NamedClassTyped[_]] == NamedClassTyped[Seq[String]]("v5") mustBe true
      seqDoubleKey.parseJson.convertTo[NamedClassTyped[_]] == NamedClassTyped[Seq[Double]]("v6") mustBe true
    }

  }

}
