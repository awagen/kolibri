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

import de.awagen.kolibri.datatypes.fixtures.ValuesFixtures
import de.awagen.kolibri.datatypes.io.ValuesIO
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.utils.ParameterUtils._
import de.awagen.kolibri.datatypes.utils.{MathUtils, OrderedMultiValuesBatchUtils, SequenceUtils}
import de.awagen.kolibri.datatypes.values.{DistinctValues, RangeValues}

class GridOrderedMultiValuesSpec extends UnitTestSpec {

  "GridExperiment" must {

    "be correctly transformed into parameter sets" in {
      val experimentObj: GridOrderedMultiValues = ValuesIO.jsValueToGridOrderedMultiValues(ValuesFixtures.experiment)
      val values: Seq[Seq[Any]] = experimentObj.findNNextElementsFromPosition(0, 1000)
      val names: Seq[String] = experimentObj.getParameterNameSequence
      names.size mustBe 2
      names.head mustBe "testParam1"
      names(1) mustBe "testParam2"
      //each range with given step size has 11 values -> 121 combinations
      values.size mustBe 121
      values.foreach(x => x.size mustBe 2)
    }

    "be correctly transformed into parameter sets in case first parameter has only one value" in {
      //given
      val parameter1: RangeValues[Float] = RangeValues[Float]("cf.rcw", 0.0f, 0.0f, 0.01f)
      val parameter2: RangeValues[Float] = RangeValues[Float]("cf.predictw", 0.0f, 1.0f, 0.2f)
      val parameter3: DistinctValues[String] = DistinctValues("ottoquery", List("true", "false"))
      val values: GridOrderedMultiValues = GridOrderedMultiValues(List(parameter1, parameter2, parameter3))
      //when,then
      values.numberOfCombinations mustBe 12
      SequenceUtils.areSameSeq(values.findNNextElementsFromPosition(0, 22), Seq(Seq(0.0f, 0.0f, "true"), Seq(0.0f, 0.2f, "true"),
        Seq(0.0f, 0.4f, "true"), Seq(0.0f, 0.6f, "true"), Seq(0.0f, 0.8f, "true"), Seq(0.0f, 1.0f, "true"),
        Seq(0.0f, 0.0f, "false"), Seq(0.0f, 0.2f, "false"),
        Seq(0.0f, 0.4f, "false"), Seq(0.0f, 0.6f, "false"), Seq(0.0f, 0.8f, "false"), Seq(0.0f, 1.0f, "false"))) mustBe true
      SequenceUtils.areSame(values.findNthElement(0).get, Seq(0.0f, 0.0f, "true"))
      SequenceUtils.areSame(values.findNthElement(1).get, Seq(0.0f, 0.2f, "true"))
      SequenceUtils.areSame(values.findNthElement(2).get, Seq(0.0f, 0.4f, "true"))
      SequenceUtils.areSame(values.findNthElement(3).get, Seq(0.0f, 0.6f, "true"))
      SequenceUtils.areSame(values.findNthElement(4).get, Seq(0.0f, 0.8f, "true"))
      SequenceUtils.areSame(values.findNthElement(5).get, Seq(0.0f, 1.0f, "true"))
      SequenceUtils.areSame(values.findNthElement(11).get, Seq(0.0f, 1.0f, "false"))
    }

    "correctly translate parameter names and values to query strings" in {
      val labels: Seq[String] = List("t1", "t2")
      val sample1: List[Float] = List(1.0f, 1.5f)
      val sample2: List[Float] = List(0.5f, 0.1f)
      val values: Seq[Seq[Float]] = List(sample1, sample2)
      val qStrings: Seq[String] = parameterValuesToQueryStrings((labels, values))
      val expected: Seq[String] = List("t1=1.00&t2=1.50", "t1=0.50&t2=0.10")
      qStrings mustBe expected
    }

    "correctly divide itself into batches" in {
      val experimentVals: GridOrderedMultiValues = ValuesFixtures.gridExperimentThreeParamWithStringValue
      val batches: Seq[OrderedMultiValuesBatch] = OrderedMultiValuesBatchUtils.splitIntoBatchesOfSize(ValuesFixtures.gridExperimentThreeParamWithStringValue, 10)
      batches.size mustBe 25 // 11 * 11 * 2 / 10, last batch separate and not full
      for (a <- batches.indices) {
        batches(a).batchNr mustBe (a + 1)
        batches(a).batchSize mustBe 10
        batches(a).values mustBe experimentVals.values
      }
    }

    "correctly find n-th element" in {
      val experiment: GridOrderedMultiValues = ValuesFixtures.gridExperimentThreeParam
      val nthFound1 = experiment.findNthElement(0).get.asInstanceOf[Seq[Float]]
      val nthFound2 = experiment.findNthElement(1).get.asInstanceOf[Seq[Float]]
      val nthFound3 = experiment.findNthElement(2).get.asInstanceOf[Seq[Float]]
      val nthFound4 = experiment.findNthElement(3).get.asInstanceOf[Seq[Float]]
      val nthFound5 = experiment.findNthElement(4).get.asInstanceOf[Seq[Float]]
      val nthFound6 = experiment.findNthElement(5).get.asInstanceOf[Seq[Float]]
      val nthFound7 = experiment.findNthElement(6).get.asInstanceOf[Seq[Float]]
      val nthFound8 = experiment.findNthElement(7).get.asInstanceOf[Seq[Float]]
      val nthFound9 = experiment.findNthElement(8).get.asInstanceOf[Seq[Float]]
      val nthFound10 = experiment.findNthElement(9).get.asInstanceOf[Seq[Float]]
      val nthFound11 = experiment.findNthElement(10).get.asInstanceOf[Seq[Float]]
      val nthFound12 = experiment.findNthElement(11).get.asInstanceOf[Seq[Float]]
      MathUtils.equalWithPrecision(nthFound1, List(0.0f, 0.0f, 0.0f), 0.00001f) mustBe true
      MathUtils.equalWithPrecision(nthFound2, List(0.1f, 0.0f, 0.0f), 0.00001f) mustBe true
      MathUtils.equalWithPrecision(nthFound3, List(0.2f, 0.0f, 0.0f), 0.00001f) mustBe true
      MathUtils.equalWithPrecision(nthFound4, List(0.3f, 0.0f, 0.0f), 0.00001f) mustBe true
      MathUtils.equalWithPrecision(nthFound5, List(0.4f, 0.0f, 0.0f), 0.00001f) mustBe true
      MathUtils.equalWithPrecision(nthFound6, List(0.5f, 0.0f, 0.0f), 0.00001f) mustBe true
      MathUtils.equalWithPrecision(nthFound7, List(0.6f, 0.0f, 0.0f), 0.00001f) mustBe true
      MathUtils.equalWithPrecision(nthFound8, List(0.7f, 0.0f, 0.0f), 0.00001f) mustBe true
      MathUtils.equalWithPrecision(nthFound9, List(0.8f, 0.0f, 0.0f), 0.00001f) mustBe true
      MathUtils.equalWithPrecision(nthFound10, List(0.9f, 0.0f, 0.0f), 0.00001f) mustBe true
      MathUtils.equalWithPrecision(nthFound11, List(1.0f, 0.0f, 0.0f), 0.00001f) mustBe true
      MathUtils.equalWithPrecision(nthFound12, List(0.0f, 0.2f, 0.0f), 0.00001f) mustBe true
    }

    "find same nth elements as generated" in {
      val experiment: GridOrderedMultiValues = ValuesFixtures.gridExperimentThreeParam
      val paramSeries: Seq[Seq[Any]] = experiment.findNNextElementsFromPosition(0, 5000)

      for (i <- paramSeries.indices) {
        val nthFound = experiment.findNthElement(i).get.asInstanceOf[Seq[Float]]
        val nthGenerated = paramSeries(i).asInstanceOf[Seq[Float]]
        val compResult: Boolean = MathUtils.equalWithPrecision(nthFound, nthGenerated, 0.00001f)
        compResult mustBe true
      }
    }

    "correctly iterate from n-th element" in {
      val experiment: GridOrderedMultiValues = ValuesFixtures.gridExperimentThreeParam
      val paramSeries: Seq[Seq[Any]] = experiment.findNNextElementsFromPosition(0, 5000)

      val allValues: Seq[Seq[Any]] = experiment.findNNextElementsFromPosition(0, paramSeries.length)

      paramSeries.size mustBe 1331
      allValues.size mustBe 1331
      for (i <- allValues.indices) {
        val compResult: Boolean = MathUtils.equalWithPrecision(allValues(i).asInstanceOf[Seq[Float]],
          paramSeries(i).asInstanceOf[Seq[Float]], 0.000001f)
        compResult mustBe true
      }
    }

    "respect range to pick" in {
      val experiment: GridOrderedMultiValues = ValuesFixtures.gridExperimentThreeParam
      val paramSeries: Seq[Seq[Any]] = experiment.findNNextElementsFromPosition(0, 100)

      paramSeries.size mustBe 100
    }

    "correctly provide parameter names and sequence of values" in {
      val experiment: GridOrderedMultiValues = ValuesFixtures.gridExperimentTwoParam
      val parameterSequence: Seq[Seq[Any]] = experiment.findNNextElementsFromPosition(0, 1000)

      parameterSequence.size mustBe 121
      for (a <- parameterSequence.indices) {
        parameterSequence(a).size mustBe 2
      }

      val firstEleven: Seq[Seq[Float]] = List(List(0.0f, 0.0f), List(0.1f, 0.0f), List(0.2f, 0.0f), List(0.3f, 0.0f),
        List(0.4f, 0.0f), List(0.5f, 0.0f), List(0.6f, 0.0f), List(0.7f, 0.0f), List(0.8f, 0.0f),
        List(0.9f, 0.0f), List(1.0f, 0.0f))

      val lastTen: Seq[Seq[Float]] = List(List(0.1f, 2.0f), List(0.2f, 2.0f), List(0.3f, 2.0f),
        List(0.4f, 2.0f), List(0.5f, 2.0f), List(0.6f, 2.0f), List(0.7f, 2.0f), List(0.8f, 2.0f),
        List(0.9f, 2.0f), List(1.0f, 2.0f))

      MathUtils.seqEqualWithPrecision(parameterSequence.slice(0, 11).asInstanceOf[Seq[Seq[Float]]], firstEleven, 0.0001f) mustBe true
      MathUtils.seqEqualWithPrecision(parameterSequence.slice(111, 121).asInstanceOf[Seq[Seq[Float]]], lastTen, 0.0001f) mustBe true
    }

    "generate parameters correctly for added parameters" in {
      //given
      val orderedValues1: RangeValues[Float] = RangeValues("p1", 0.0f, 0.2f, 0.1f)
      val orderedValues2: RangeValues[Float] = RangeValues("p2", 1.0f, 2.0f, 1.0f)
      val values: OrderedMultiValues = GridOrderedMultiValues(Seq(orderedValues1, orderedValues2))
      val addValues = DistinctValues[String]("p3", Seq("hose", "hemd"))
      val valuesAdded: OrderedMultiValues = values.addValue(addValues, prepend = false)
      //when
      val iniAllValues: Seq[Seq[Any]] = values.findNNextElementsFromPosition(0, 1000)
      val afterAddAllValues: Seq[Seq[Any]] = valuesAdded.findNNextElementsFromPosition(0, 1000)
      //then
      iniAllValues.size mustBe 6
      afterAddAllValues.size mustBe 12
      SequenceUtils.areSame(iniAllValues, Seq(Seq(0.0f, 1.0f), Seq(0.1f, 1.0f), Seq(0.2f, 1.0f), Seq(0.0f, 2.0f),
        Seq(0.1f, 2.0f), Seq(0.2f, 2.0f))) mustBe true
      SequenceUtils.areSame(afterAddAllValues, Seq(Seq(0.0f, 1.0f, "hose"), Seq(0.1f, 1.0f, "hose"), Seq(0.2f, 1.0f, "hose"), Seq(0.0f, 2.0f, "hose"),
        Seq(0.1f, 2.0f, "hose"), Seq(0.2f, 2.0f, "hose"),
        Seq(0.0f, 1.0f, "hemd"), Seq(0.1f, 1.0f, "hemd"), Seq(0.2f, 1.0f, "hemd"), Seq(0.0f, 2.0f, "hemd"),
        Seq(0.1f, 2.0f, "hemd"), Seq(0.2f, 2.0f, "hemd"))) mustBe true
    }

  }

}
