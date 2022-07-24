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

import de.awagen.kolibri.datatypes.io.json.OrderedMultiValuesJsonProtocol._
import de.awagen.kolibri.datatypes.io.json.OrderedValuesJsonProtocol.DISTINCT_VALUES_TYPE
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, GridOrderedMultiValuesBatch, OrderedMultiValues}
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.values.DistinctValues
import spray.json._

class OrderedMultiValuesJsonProtocolSpec extends UnitTestSpec {

  "OrderedMultiValues" must {

    "correctly be parsed to GridOrderedMultiValues" in {
      //given
      val jsonDouble: JsValue =
        s"""{"type": "GRID_FROM_VALUES_SEQ_TYPE", "values":[{"type": "$DISTINCT_VALUES_TYPE", "name": "test", "values": [0.45, 0.32]}]}""".parseJson
      val jsonString: JsValue =
        s"""{"type": "GRID_FROM_VALUES_SEQ_TYPE", "values":[{"type": "$DISTINCT_VALUES_TYPE", "name": "test", "values": ["v1", "v2"]}]}""".parseJson
      //when, then
      jsonDouble.convertTo[GridOrderedMultiValues] mustBe GridOrderedMultiValues(Seq(DistinctValues[Double]("test", Seq(0.45, 0.32))))
      jsonDouble.convertTo[OrderedMultiValues] mustBe GridOrderedMultiValues(Seq(DistinctValues[Double]("test", Seq(0.45, 0.32))))
      jsonString.convertTo[GridOrderedMultiValues] mustBe GridOrderedMultiValues(Seq(DistinctValues[String]("test", Seq("v1", "v2"))))
      jsonString.convertTo[OrderedMultiValues] mustBe GridOrderedMultiValues(Seq(DistinctValues[String]("test", Seq("v1", "v2"))))
    }

    "correctly be parsed to GridOrderedMultiValuesBatch" in {
      //given
      val json1: JsValue =
        s"""{"type": "GRID_BATCH_FROM_VALUES_SEQ_TYPE", "multiValues": {"values":[{"type": "$DISTINCT_VALUES_TYPE", "name": "test", "values": [0.45, 0.32]}]}, "batchSize": 10, "batchNr": 2}""".parseJson
      val json2: JsValue =
        s"""{"type": "GRID_BATCH_FROM_VALUES_SEQ_TYPE", "multiValues": {"values":[{"type": "$DISTINCT_VALUES_TYPE", "name": "test", "values": [0.45, 0.32]},{"type": "$DISTINCT_VALUES_TYPE", "name": "test1", "values": ["v10", "v20"]}]}, "batchSize": 10, "batchNr": 2}""".parseJson
      //when, then
      json1.convertTo[OrderedMultiValues] mustBe GridOrderedMultiValuesBatch(GridOrderedMultiValues(Seq(DistinctValues[Double]("test", Seq(0.45, 0.32)))), 10, 2)
      json2.convertTo[OrderedMultiValues] mustBe GridOrderedMultiValuesBatch(GridOrderedMultiValues(Seq(DistinctValues[Double]("test", Seq(0.45, 0.32)), DistinctValues[String]("test1", Seq("v10", "v20")))), 10, 2)
    }
  }


}
