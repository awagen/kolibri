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

package de.awagen.kolibri.base.processing.distribution

import de.awagen.kolibri.base.processing.ProcessingMessages.AggregationStateWithData
import de.awagen.kolibri.base.processing.distribution.DistributionStates.{AllProvidedWaitingForResults, Completed}
import de.awagen.kolibri.base.processing.execution.expectation.BaseExecutionExpectation
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.base.traits.Traits.WithBatchNr
import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator

class FilteringOnceDistributorSpec extends UnitTestSpec {

  case class IntWithBatch(batchNr: Int, value: Int) extends WithBatchNr

  private[this] def distributor[T <: WithBatchNr, U](elements: Seq[T],
                                                     acceptOnlyIds: Seq[Int]): Distributor[T, U] =
    new FilteringOnceDistributor[T, U](
      maxParallel = 3,
      generator = ByFunctionNrLimitedIndexedGenerator.createFromSeq(
        elements
      ),
      acceptOnlyIds.toSet
    )

  "FilteringOnceDistributor" should {

    "correctly provide elements and accept only ids provided" in {
      // given
      val intDistributor = distributor[IntWithBatch, Int](Range(2, 5).map(x => IntWithBatch(x, x)), Range(0, 5))
      // when, then
      intDistributor.next mustBe Right(Seq(2, 3, 4).map(x => IntWithBatch(x, x)))
      intDistributor.next mustBe Left(AllProvidedWaitingForResults)
      intDistributor.accept(AggregationStateWithData(1, "jobId", 2, BaseExecutionExpectation.empty()))
      intDistributor.accept(AggregationStateWithData(1, "jobId", 3, BaseExecutionExpectation.empty()))
      intDistributor.accept(AggregationStateWithData(1, "jobId", 4, BaseExecutionExpectation.empty()))
      intDistributor.accept(AggregationStateWithData(1, "jobId", 0, BaseExecutionExpectation.empty()))
      intDistributor.accept(AggregationStateWithData(1, "jobId", 1, BaseExecutionExpectation.empty()))
      intDistributor.accept(AggregationStateWithData(1, "jobId", 5, BaseExecutionExpectation.empty()))
      intDistributor.next mustBe Left(Completed)
    }

  }

}
