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

package de.awagen.kolibri.datatypes.utils

import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues, OrderedMultiValuesBatch}
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.values.{DistinctValues, RangeValues}

class OrderedMultiValuesBatchUtilsSpec extends UnitTestSpec {

  //combinations in total: 3 * 11 * 4 * 11 = 1452
  val parameters: OrderedMultiValues = GridOrderedMultiValues(Seq(
    DistinctValues("p1", Seq("p1v1", "p1v2", "p1v3")),
    RangeValues[Float]("p2", 0.0f, 1.0f, 0.1f),
    DistinctValues("p3", Seq("p3v1", "p3v2", "p3v3", "p3v4")),
    RangeValues[Float]("p4", 0.0f, 2.0f, 0.2f)
  ))

  "BatchGenerator" must {
    "correctly split into batches by parameter" in {
      // given
      val orderedValues_1 = DistinctValues[Int]("v1", Seq(1, 2))
      val orderedValues_2 = DistinctValues[String]("v2", Seq("a", "b", "c"))
      val multiValues = GridOrderedMultiValues(Seq(orderedValues_1, orderedValues_2))
      // when
      val byParamMultiValues: Seq[OrderedMultiValues] = OrderedMultiValuesBatchUtils.splitIntoBatchByParameter(multiValues, "v1").toSeq
      // then
      byParamMultiValues.size mustBe 2
      byParamMultiValues.head.findNthElement(0).get mustBe Seq(1, "a")
      byParamMultiValues.head.findNthElement(1).get mustBe Seq(1, "b")
      byParamMultiValues.head.findNthElement(2).get mustBe Seq(1, "c")
      byParamMultiValues(1).findNthElement(0).get mustBe Seq(2, "a")
      byParamMultiValues(1).findNthElement(1).get mustBe Seq(2, "b")
      byParamMultiValues(1).findNthElement(2).get mustBe Seq(2, "c")
    }

    "throw assertion error if parameter to split by not in parameter names" in {
      // given
      val orderedValues_1 = DistinctValues[Int]("v1", Seq(1, 2))
      val orderedValues_2 = DistinctValues[String]("v2", Seq("a", "b", "c"))
      val multiValues = GridOrderedMultiValues(Seq(orderedValues_1, orderedValues_2))
      // when
      assertThrows[AssertionError] {
        OrderedMultiValuesBatchUtils.splitIntoBatchByParameter(multiValues, "v3")
      }
    }

    "correctly split into batches of size" in {
      //given,when
      val split1: Seq[OrderedMultiValuesBatch] = OrderedMultiValuesBatchUtils.splitIntoBatchesOfSize(parameters, 1000)
      val split2: Seq[OrderedMultiValuesBatch] = OrderedMultiValuesBatchUtils.splitIntoBatchesOfSize(parameters, 200)
      //then
      split1.size mustBe 2
      split2.size mustBe 8
    }

    "correctly split into at most number of batches" in {
      //given,when
      val split1: Seq[OrderedMultiValuesBatch] = OrderedMultiValuesBatchUtils.splitIntoAtMostNrOfBatches(parameters, 2)
      val split2: Seq[OrderedMultiValuesBatch] = OrderedMultiValuesBatchUtils.splitIntoAtMostNrOfBatches(parameters, 1500)
      val split3: Seq[OrderedMultiValuesBatch] = OrderedMultiValuesBatchUtils.splitIntoAtMostNrOfBatches(parameters, 5)
      //then
      split1.size mustBe 2
      split1.head.numberOfCombinations mustBe 726
      split1(1).numberOfCombinations mustBe 726
      split2.size mustBe 1452
      split2.foreach(x => x.numberOfCombinations mustBe 1)
      split3.size mustBe 5
      split3.head.numberOfCombinations mustBe 291
      split3(1).numberOfCombinations mustBe 291
      split3(2).numberOfCombinations mustBe 291
      split3(3).numberOfCombinations mustBe 291
      split3(4).numberOfCombinations mustBe 288
    }

    "correctly find batch of parameter combinations" in {
      //given, when
      val batch = OrderedMultiValuesBatchUtils.findBatch(parameters, 291, 5)
      //then
      batch.size mustBe 288
      batch mustBe parameters.findNNextElementsFromPosition(291 * 4, 291)
    }
  }

}
