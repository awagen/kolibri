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

package de.awagen.kolibri.base.processing

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.AggregationState
import de.awagen.kolibri.base.processing.DistributionStates.{AllProvidedWaitingForResults, Completed, DistributionState, Pausing}
import de.awagen.kolibri.base.traits.Traits.WithBatchNr
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import org.slf4j.{Logger, LoggerFactory}

/**
  * Basic distributor that just provides batches up to the max of concurrently processed
  * batches. After providing the batches, keeps track of those for which no result
  * retrieved yet. Result can either be the corresponding AggregationState or
  * marking as failed
  *
  * @param maxParallel    - max elements to provide as in progress at the same time
  * @param generator      - generator providing the elements of type T
  * @param resultConsumer - consumer of the aggregated result of tyoe AggregationState[T]
  * @tparam T - type of elements provided by generator
  * @tparam U - type of the aggregation
  */
class ProcessOnceDistributor[T <: WithBatchNr, U](private[this] var maxParallel: Int,
                                                  generator: IndexedGenerator[T],
                                                  resultConsumer: AggregationState[U] => ()) extends Distributor[T, U] {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private[this] val iterator: Iterator[T] = generator.iterator
  private[this] var failed: Seq[Int] = Seq.empty
  private[this] var inProgress: Seq[Int] = Seq.empty
  private[this] var distributedBatchCount: Int = 0
  private[this] var numResultsReceivedCount: Int = 0

  private[processing] def removeBatchRecords(batchNr: Int): Unit = {
    failed = failed.filter(_ != batchNr)
    inProgress = inProgress.filter(_ != batchNr)
  }

  private[processing] def provideNextBatchesAndUpdateProgressState: Seq[T] = {
    var availableSlots: Int = freeSlots
    var addedElements: Seq[T] = Seq.empty
    while (iterator.hasNext && availableSlots > 0) {
      addedElements = addedElements :+ iterator.next()
      availableSlots -= 1
    }
    inProgress = inProgress ++ addedElements.map(x => x.batchNr)
    distributedBatchCount += addedElements.size
    addedElements
  }

  def maxBatchesAreRunning: Boolean = idsInProgress.size >= maxInParallel

  def freeSlots: Int = maxInParallel - idsInProgress.size

  def setMaxParallelCount(count: Int): Unit = {
    maxParallel = count
  }

  def accept(element: AggregationState[U]): Boolean = {
    logger.debug(s"distributor: received aggregation state: $element")
    if ((failed ++ inProgress).contains(element.batchNr)) {
      resultConsumer.apply(element)
      removeBatchRecords(element.batchNr)
      numResultsReceivedCount += 1
      true
    }
    else false
  }

  def markAsFail(batchNr: Int): Unit = {
    failed = failed :+ batchNr
    inProgress = inProgress.filter(_ != batchNr)
  }

  def hasUnsentBatches: Boolean = iterator.hasNext

  def unfinished: Seq[Int] = failed ++ inProgress

  def next: Either[DistributionState, Seq[T]] = {
    if (maxBatchesAreRunning) {
      if (iterator.hasNext) Left(Pausing)
      else Left(AllProvidedWaitingForResults)
    }
    else if (hasUnsentBatches) {
      Right(provideNextBatchesAndUpdateProgressState)
    }
    else if (inProgress.nonEmpty) Left(AllProvidedWaitingForResults)
    else Left(Completed)
  }

  def idsInProgress: Seq[Int] = inProgress

  def maxInParallel: Int = maxParallel

  def idsFailed: Seq[Int] = failed

  override def nrDistributed: Int = distributedBatchCount

  override def nrResultsAccepted: Int = numResultsReceivedCount
}
