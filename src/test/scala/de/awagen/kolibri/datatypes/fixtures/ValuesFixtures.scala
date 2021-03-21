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

package de.awagen.kolibri.datatypes.fixtures

import de.awagen.kolibri.datatypes.io.ValuesIO
import de.awagen.kolibri.datatypes.multivalues.GridOrderedMultiValues
import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues, RangeValues}
import play.api.libs.json.JsValue

object ValuesFixtures {

  val experimentConfig: String = "{\n \"name\": \"testExperiment\",\n \"params\":  [\n   {\n    \"name\": \"testParam1\",\n    \"start\": 0.0,\n    \"end\": 1.0,\n    \"stepSize\": 0.1,\n    \"precision\": 0.00001\n   },\n   {\n    \"name\": \"testParam2\",\n    \"start\": 0.0,\n    \"end\": 1.0,\n    \"stepSize\": 0.1,\n    \"precision\": 0.00001\n   }   \n ]\t\n}"

  val experiment: JsValue = ValuesIO.jsonStringToJsValue(experimentConfig)

  val parameter1: OrderedValues[Float] = RangeValues[Float]("testParam1", 0.0f, 1.0f, 0.1f)
  val parameter2: OrderedValues[Float] = RangeValues[Float]("testParam2", 0.0f, 2.0f, 0.2f)
  val parameter3: OrderedValues[Float] = RangeValues[Float]("testParam3", 0.0f, 0.1f, 0.01f)
  val parameter4: OrderedValues[String] = DistinctValues("testParam4", List("val1", "val2"))
  val gridExperimentThreeParamWithStringValue: GridOrderedMultiValues = GridOrderedMultiValues(Seq(parameter1, parameter2, parameter4))
  val gridExperimentTwoParam: GridOrderedMultiValues = GridOrderedMultiValues(Seq(parameter1, parameter2))
  val gridExperimentThreeParam: GridOrderedMultiValues = GridOrderedMultiValues(List(parameter1, parameter2, parameter3))

  val exp: Seq[Seq[Any]] = gridExperimentThreeParamWithStringValue.findNNextElementsFromPosition(0, 10)
  val exp1: Seq[Seq[Any]] = gridExperimentThreeParamWithStringValue.findNNextElementsFromPosition(0, 20)
}
