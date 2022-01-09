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

package de.awagen.kolibri.base.actors.work.worker

import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.base.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.TagType.TagType
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.values.DataPoint

object ProcessingMessages {

  /**
   * Trait for data points in processing stages
   * @tparam T
   */
  sealed trait ProcessingMessage[+T] extends KolibriSerializable with TaggedWithType with DataPoint[T] {
    val data: T

    def withTags(tagType: TagType, tags: Set[Tag]): ProcessingMessage[T] = {
      this.addTags(tagType, tags)
      this
    }
  }

  case class Corn[+T](data: T, weight: Double = 1.0) extends ProcessingMessage[T]

  case class BadCorn[+T](failType: TaskFailType, weight: Double = 1.0) extends ProcessingMessage[T] {
    override val data: T = null.asInstanceOf[T]
  }

  /**
   * Trait for aggregation states
   * @tparam T
   */
  sealed trait AggregationState[+T] extends KolibriSerializable with TaggedWithType {
    val jobID: String
    val batchNr: Int
    val executionExpectation: ExecutionExpectation
  }

  case class AggregationStateWithoutData[+V](containedElementCount: Int,
                                             jobID: String,
                                             batchNr: Int,
                                             executionExpectation: ExecutionExpectation) extends AggregationState[V]

  case class AggregationStateWithData[+V](data: V,
                                          jobID: String,
                                          batchNr: Int,
                                          executionExpectation: ExecutionExpectation) extends AggregationState[V]


  /**
   * Summary of current state of results
   * @param result - enum value of type ProcessingResult
   * @param nrOfBatchesTotal - total number of batches in the job
   * @param nrOfBatchesSentForProcessing - nr of batches that were sent to be processed
   * @param nrOfResultsReceived - nr of batch results that were already received
   * @param failedBatches - the indices for the batches that were observed as failed (depending on distribution strategy
   *                      they might still be retried later, so if index appears here temporarilly, might disappear
   *                      during further processing if processed successfully)
   */
  case class ResultSummary(result: ProcessingResult.Value,
                           nrOfBatchesTotal: Int,
                           nrOfBatchesSentForProcessing: Int,
                           nrOfResultsReceived: Int,
                           failedBatches: Seq[Int]) extends KolibriSerializable {

    override def toString: String = Map(
      "result" -> result,
      "nrOfBatchesTotal" -> nrOfBatchesTotal,
      "nrOfBatchesSentForProcessing" -> nrOfBatchesSentForProcessing,
      "nrOfResultsReceived" -> nrOfResultsReceived,
      "failedBatches" -> failedBatches).toString

  }

  def unknownJobResultSummary: ResultSummary = {
    ResultSummary(
      result = ProcessingResult.UNKNOWN,
      nrOfBatchesTotal = 0,
      nrOfBatchesSentForProcessing = 0,
      nrOfResultsReceived = 0,
      failedBatches = Seq.empty
    )
  }

  /**
   * Enum for distinct processing states
   */
  object ProcessingResult extends Enumeration {
    val SUCCESS, FAILURE, RUNNING, UNKNOWN = Value
  }

}
