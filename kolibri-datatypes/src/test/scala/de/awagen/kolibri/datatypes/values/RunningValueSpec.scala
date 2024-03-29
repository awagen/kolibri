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


package de.awagen.kolibri.datatypes.values

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.values.RunningValues._
import de.awagen.kolibri.datatypes.values.aggregation.AggregationTestHelper.runningValueSupplier


class RunningValueSpec extends UnitTestSpec {

  "Map running value" must {

    "correctly calculate un-weighted add" in {
      // given
      val value1: RunningValue[Map[String, Double]] = mapValueUnweightedSumRunningValue(1.0, 1, Map("key1" -> 1.0))
      val value2: RunningValue[Map[String, Double]] = mapValueUnweightedSumRunningValue(2.0, 1, Map("key1" -> 1.0, "key2" -> 2.0))
      // when
      val addedValue = value1.add(value2)
      // then
      addedValue.value mustBe Map("key1" -> 2.0, "key2" -> 2)
    }

    "correctly calculate weighted add" in {
      // given
      val value1: RunningValue[Map[String, Double]] = mapValueWeightedSumRunningValue(1.0, 1, Map("key1" -> 1.0))
      val value2: RunningValue[Map[String, Double]] = mapValueWeightedSumRunningValue(2.0, 1, Map("key1" -> 1.0, "key2" -> 2.0))
      // when
      val addedValue = value1.add(value2)
      // then
      addedValue.value mustBe Map("key1" -> 3.0, "key2" -> 4.0)
    }

  }

  "Nested Map running value" must {

    "correctly calculate weighted add" in {
      val value1: RunningValue[Map[String, Map[String, Double]]] = nestedMapValueWeightedSumUpRunningValue(1.0, 1, Map("key1" -> Map("1" -> 1.0)))
      val value2: RunningValue[Map[String, Map[String, Double]]] = nestedMapValueWeightedSumUpRunningValue(2.0, 1,
        Map("key1" -> Map("1" -> 1.0, "2" -> 2.0), "key2" -> Map("4" -> 5.0))
      )
      // when
      val addedValue = value1.add(value2)
      // then
      addedValue.value mustBe Map("key1" -> Map("1" -> 3.0, "2" -> 4.0), "key2" -> Map("4" -> 10.0))
    }

    "correctly calculate un-weighted add" in {
      val value1: RunningValue[Map[String, Map[String, Double]]] = nestedMapValueUnweightedSumUpRunningValue(1.0, 1,
        Map("key1" -> Map("1" -> 1.0)))
      val value2: RunningValue[Map[String, Map[String, Double]]] = nestedMapValueUnweightedSumUpRunningValue(2.0, 1,
        Map("key1" -> Map("1" -> 1.0, "2" -> 2.0), "key2" -> Map("4" -> 5.0))
      )
      // when
      val addedValue = value1.add(value2)
      // then
      addedValue.value mustBe Map("key1" -> Map("1" -> 2.0, "2" -> 2.0), "key2" -> Map("4" -> 5.0))
    }

    "correctly add many values" in {
      // given
      var value: RunningValue[Map[String, Map[String, Double]]] = runningValueSupplier.apply()
      // when
      Range(0,100).foreach(_ => {
        value = value.add(runningValueSupplier.apply()).asInstanceOf[RunningValue[Map[String, Map[String, Double]]]]
      })
      // then
      value.value mustBe Map("key1" -> Map("1" -> 101.0))
    }

  }

}
