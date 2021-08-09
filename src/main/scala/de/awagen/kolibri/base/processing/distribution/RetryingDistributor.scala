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
import de.awagen.kolibri.base.traits.Traits.WithBatchNr
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import org.slf4j.{Logger, LoggerFactory}


class RetryingDistributor[T <: WithBatchNr, U](private[this] var maxParallel: Int,
                                               generator: IndexedGenerator[T],
                                               private[this] var maxNrRetries: Int) extends Distributor[T, U] {

  private[processing] var completed: Boolean = false

  private[processing] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private[processing] var currentNrRetries: Int = 0

  private[processing] var distributedBatchCount: Int = 0

  private[processing] var numResultsReceivedCount: Int = 0

  private[processing] var currentDistributor: Distributor[T, U] = new ProcessOnceDistributor[T, U](
    maxParallel,
    generator)

  def retryDistributor: Distributor[T, U] = {
    val unfinishedSoFar: Seq[Int] = currentDistributor.unfinished
    new FilteringOnceDistributor[T, U](
      maxParallel,
      ByFunctionNrLimitedIndexedGenerator.createFromSeq(
        idsFailed.map(x => generator.get(x).get)
      ),
      unfinishedSoFar.toSet)
  }

  override def setMaxParallelCount(count: Int): Unit = {
    maxParallel = count
    currentDistributor.setMaxParallelCount(count)
  }

  override def maxInParallel: Int = maxParallel

  override def idsFailed: Seq[Int] = currentDistributor.idsFailed

  override def idsInProgress: Seq[Int] = currentDistributor.idsInProgress

  override def unfinished: Seq[Int] = currentDistributor.unfinished

  override def markAsFail(identifier: Int): Unit = currentDistributor.markAsFail(identifier)

  override def accept(element: AggregationState[U]): Boolean = {
    val didAccept: Boolean = currentDistributor.accept(element)
    if (didAccept) {
      logger.info(s"accepted result for batch: ${element.batchNr}")
      numResultsReceivedCount += 1
    }
    else {
      logger.warn(s"state received but not accepted: ${element.batchNr}")
      logger.debug(s"not accepted state: $element")
    }
    didAccept
  }

  override def next: Either[DistributionStates.DistributionState, Seq[T]] = {
    currentDistributor.next match {
      case nxt@Left(e) if e == Pausing => nxt
      case nxt@Left(e) if e == AllProvidedWaitingForResults => nxt
      case nxt@Left(e) if e == Completed =>
        logger.debug(s"completed - in processing: $idsInProgress")
        if (idsFailed.nonEmpty && currentNrRetries < maxNrRetries) {
          logger.info(s"switching to retry nr: ${currentNrRetries + 1}")
          currentDistributor = retryDistributor
          currentNrRetries += 1
          next
        }
        else {
          completed = true
          nxt
        }
      case e@Right(batches) =>
        if (currentNrRetries == 0) {
          distributedBatchCount += batches.size
        }
        logger.debug(s"providing batches - in processing: $idsInProgress")
        e
    }
  }

  override def nrDistributed: Int = distributedBatchCount

  override def nrResultsAccepted: Int = numResultsReceivedCount

  override def hasCompleted: Boolean = completed
}
