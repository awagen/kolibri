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
import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues, RangeValues}
import spray.json._


class OrderedValuesJsonProtocolSpec extends UnitTestSpec {

  "DistinctValues" must {

    "correctly be parsed to DistinctValues[Double]" in {
      //given
      import de.awagen.kolibri.datatypes.io.json.OrderedValuesJsonProtocol._
      val json: JsValue = """{"name": "test", "values": [0.45, 0.32]}""".parseJson
      //when, then
      json.convertTo[DistinctValues[Double]] mustBe DistinctValues[Double]("test", Seq(0.45, 0.32))
      json.convertTo[OrderedValues[Any]] mustBe DistinctValues[Double]("test", Seq(0.45, 0.32))
    }

    "correctly be parsed to DistinctValues[String]" in {
      //given
      import de.awagen.kolibri.datatypes.io.json.OrderedValuesJsonProtocol._
      val json: JsValue = """{"name": "test", "values": ["val1", "val2"]}""".parseJson
      //when, then
      json.convertTo[DistinctValues[String]] mustBe DistinctValues[String]("test", Seq("val1", "val2"))
      json.convertTo[OrderedValues[Any]] mustBe DistinctValues[String]("test", Seq("val1", "val2"))
    }

    "correctly write DistinctValues" in {
      //given
      import de.awagen.kolibri.datatypes.io.json.OrderedValuesJsonProtocol._
      val value1 = DistinctValues[String]("key1", Seq("v1", "v2"))
      val value2 = DistinctValues[Double]("key1", Seq(0.2, 0.45))
      //when, then
      val json1: JsValue = OrderedValuesAnyFormat.write(value1)
      val json2: JsValue = OrderedValuesAnyFormat.write(value2)
      json1.toString() mustBe """{"name":"key1","values":["v1","v2"]}"""
      json2.toString() mustBe """{"name":"key1","values":[0.2,0.45]}"""
    }
  }

  "RangeValues" must {

    "correctly be parsed to RangeValues[Double]" in {
      //given
      import de.awagen.kolibri.datatypes.io.json.OrderedValuesJsonProtocol._
      val json: JsValue = """{"name": "test", "start": 0.45, "end": 0.9, "stepSize": 0.1}""".parseJson
      //when, then
      json.convertTo[RangeValues[Double]] mustBe RangeValues[Double]("test", 0.45, 0.9, 0.1)
      json.convertTo[OrderedValues[Any]] mustBe RangeValues[Double]("test", 0.45, 0.9, 0.1)
    }

    "correctly write RangeValues[Double]" in {
      //given
      import de.awagen.kolibri.datatypes.io.json.OrderedValuesJsonProtocol._
      val value1 = RangeValues[Double]("test", 0.45, 0.9, 0.1)
      //when, then
      val json1: JsValue = OrderedValuesAnyFormat.write(value1)
      json1.toString() mustBe """{"end":0.9,"name":"test","start":0.45,"stepSize":0.1}"""
    }

  }
}
