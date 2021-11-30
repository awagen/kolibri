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

package de.awagen.kolibri.datatypes.multivalues

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.values.{DistinctValues, RangeValues}

class GridOrderedMultiValuesBatchSpec extends UnitTestSpec {

  val testValues = List(RangeValues("p1", 0.0f, 1.0f, 0.1f), RangeValues("p2", 0.0f, 10.0f, 1.0f),
    DistinctValues("p3", List("val1", "val2"))) //121 * 2 = 242 total combinations
  val testOrderedMultiValues = GridOrderedMultiValues(testValues)

  "GridOrderedMultiValuesBatch" must {

    "values object corresponds to the original OrderedValue objects" in {
      //given
      val testBatch = GridOrderedMultiValuesBatch(testOrderedMultiValues, 100 , 2)
      //when, then
      testBatch.values.size mustBe 3
      GridOrderedMultiValues(testBatch.values).numberOfCombinations mustBe 242
    }

    "split into correctly sized batches" in {
      //given
      val testBatch = GridOrderedMultiValuesBatch(testOrderedMultiValues, 100 , 2)
      //when, then
      testBatch.numberOfCombinations mustBe 100
    }

    "correctly calculate elements corresponding to batch" in {
      //given
      val testBatch1 = GridOrderedMultiValuesBatch(testOrderedMultiValues, 11 , 2)
      //when, then
      testBatch1.findNNextElementsFromPosition(0, 11).size mustBe 11
      testBatch1.findNthElement(1).get
    }

  }

}
