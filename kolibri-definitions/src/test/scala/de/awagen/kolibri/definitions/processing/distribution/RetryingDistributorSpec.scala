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

package de.awagen.kolibri.definitions.processing.distribution

import de.awagen.kolibri.definitions.processing.ProcessingMessages.AggregationStateWithData
import de.awagen.kolibri.definitions.processing.distribution.DistributionStates.{AllProvidedWaitingForResults, Completed, Pausing}
import de.awagen.kolibri.definitions.processing.execution.expectation.BaseExecutionExpectation
import de.awagen.kolibri.definitions.testclasses.UnitTestSpec
import de.awagen.kolibri.definitions.traits.Traits.WithBatchNr
import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator

class RetryingDistributorSpec extends UnitTestSpec {

  case class IntWithBatch(batchNr: Int, value: Int) extends WithBatchNr

  private[this] def distributor[T <: WithBatchNr, U](elements: Seq[T],
                                                     maxRetries: Int): Distributor[T, U] = new RetryingDistributor[T, U](
    maxParallel = 3,
    generator = ByFunctionNrLimitedIndexedGenerator.createFromSeq(
      elements
    ),
    maxRetries
  )


  "RetryingDistributor" should {

    "correctly process with retries" in {
      val distributor: Distributor[IntWithBatch, Int] = this.distributor[IntWithBatch, Int](
        Range(0, 6).map(x => IntWithBatch(x, x)),
        1
      )
      // when, then
      distributor.next mustBe Right(Seq(0, 1, 2).map(x => IntWithBatch(x, x)))
      distributor.next mustBe Left(Pausing)
      distributor.markAsFail(0)
      distributor.accept(AggregationStateWithData(1, "", 1, BaseExecutionExpectation.empty()))
      distributor.accept(AggregationStateWithData(1, "", 2, BaseExecutionExpectation.empty()))
      distributor.next mustBe Right(Seq(3, 4, 5).map(x => IntWithBatch(x, x)))
      distributor.next mustBe Left(AllProvidedWaitingForResults)
      distributor.accept(AggregationStateWithData(1, "", 3, BaseExecutionExpectation.empty()))
      distributor.accept(AggregationStateWithData(1, "", 4, BaseExecutionExpectation.empty()))
      distributor.next mustBe Left(AllProvidedWaitingForResults)
      distributor.markAsFail(5)
      distributor.next mustBe Right(Seq(0, 5).map(x => IntWithBatch(x, x)))
      distributor.accept(AggregationStateWithData(1, "", 0, BaseExecutionExpectation.empty()))
      distributor.next mustBe Left(AllProvidedWaitingForResults)
      distributor.markAsFail(5)
      distributor.next mustBe Left(Completed)
    }

  }

}
