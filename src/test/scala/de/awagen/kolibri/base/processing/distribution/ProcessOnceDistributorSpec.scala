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

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.AggregationState
import de.awagen.kolibri.base.processing.distribution.DistributionStates.{AllProvidedWaitingForResults, Completed, Pausing}
import de.awagen.kolibri.base.processing.execution.expectation.BaseExecutionExpectation
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.base.traits.Traits.WithBatchNr
import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator

class ProcessOnceDistributorSpec extends UnitTestSpec {

  case class IntWithBatch(batchNr: Int, value: Int) extends WithBatchNr

  private[this] def distributor[T <: WithBatchNr, U](elements: Seq[T],
                                                     aggConsumer: AggregationState[U] => ()): Distributor[T, U] = new ProcessOnceDistributor[T, U](
    maxParallel = 3,
    generator = ByFunctionNrLimitedIndexedGenerator.createFromSeq(
      elements
    ),
    aggConsumer
  )

  "ProcessOnceDistributor" should {

    "correctly provide elements" in {
      // given
      var elements: Seq[Int] = Seq.empty
      val intDistributor = distributor[IntWithBatch, Int](Range(0, 10).map(x => IntWithBatch(x, x)), el => {
        elements = elements :+ el.batchNr
      })
      // when, then
      intDistributor.next mustBe Right(Seq(0, 1, 2).map(x => IntWithBatch(x, x)))
      intDistributor.next mustBe Left(Pausing)
      intDistributor.accept(AggregationState(1, "jobId", 0, BaseExecutionExpectation.empty()))
      intDistributor.next mustBe Right(Seq(3).map(x => IntWithBatch(x, x)))
      intDistributor.next mustBe Left(Pausing)
      // should not change anything if we call accept on a state with batchNr that is not
      // in the record anymore
      intDistributor.accept(AggregationState(1, "", 0, BaseExecutionExpectation.empty()))
      intDistributor.next mustBe Left(Pausing)
      // if we acceot the state for batch that hasnt even been distributed yet,
      // nothing shouzld change in state
      intDistributor.accept(AggregationState(1, "", 4, BaseExecutionExpectation.empty()))
      intDistributor.next mustBe Left(Pausing)
      intDistributor.accept(AggregationState(1, "", 1, BaseExecutionExpectation.empty()))
      intDistributor.accept(AggregationState(1, "", 2, BaseExecutionExpectation.empty()))
      intDistributor.next mustBe Right(Seq(4, 5).map(x => IntWithBatch(x, x)))
      intDistributor.next mustBe Left(Pausing)
      intDistributor.idsInProgress mustBe Seq(3, 4, 5)
      intDistributor.markAsFail(3)
      intDistributor.next mustBe Right(Seq(6).map(x => IntWithBatch(x, x)))
      intDistributor.idsInProgress mustBe Seq(4, 5, 6)
      intDistributor.accept(AggregationState(1, "", 4, BaseExecutionExpectation.empty()))
      intDistributor.accept(AggregationState(1, "", 5, BaseExecutionExpectation.empty()))
      intDistributor.accept(AggregationState(1, "", 6, BaseExecutionExpectation.empty()))
      intDistributor.next mustBe Right(Seq(7, 8, 9).map(x => IntWithBatch(x, x)))
      intDistributor.next mustBe Left(AllProvidedWaitingForResults)
      intDistributor.accept(AggregationState(1, "", 7, BaseExecutionExpectation.empty()))
      intDistributor.next mustBe Left(AllProvidedWaitingForResults)
      intDistributor.accept(AggregationState(1, "", 8, BaseExecutionExpectation.empty()))
      intDistributor.next mustBe Left(AllProvidedWaitingForResults)
      intDistributor.accept(AggregationState(1, "", 9, BaseExecutionExpectation.empty()))
      intDistributor.next mustBe Left(Completed)
    }

  }

}
