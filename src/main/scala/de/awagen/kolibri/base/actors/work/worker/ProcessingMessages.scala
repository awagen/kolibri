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

import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.ProcessingResult
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.base.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.TagType.TagType
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.values.DataPoint

object ProcessingMessages {

  sealed trait ProcessingMessage[+T] extends KolibriSerializable with TaggedWithType with DataPoint[T] {
    val data: T

    def withTags(tagType: TagType, tags: Set[Tag]): ProcessingMessage[T] = {
      this.addTags(tagType, tags)
      this
    }
  }

  case class Corn[+T](data: T) extends ProcessingMessage[T] {
    val weight: Double = 1.0
  }

  case class BadCorn[+T](failType: TaskFailType) extends ProcessingMessage[T] {
    val weight: Double = 1.0
    override val data: T = null.asInstanceOf[T]
  }

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


  case class ResultSummary(result: ProcessingResult.Value,
                           nrOfBatchesTotal: Int,
                           nrOfBatchesSentForProcessing: Int,
                           nrOfResultsReceived: Int,
                           leftoverExpectationsMap: Map[Int, ExecutionExpectation],
                           failedBatches: Seq[Int]) extends KolibriSerializable {

    override def toString: String = Map(
      "result" -> result,
      "nrOfBatchesTotal" -> nrOfBatchesTotal,
      "nrOfBatchesSentForProcessing" -> nrOfBatchesSentForProcessing,
      "nrOfResultsReceived" -> nrOfResultsReceived,
      "leftoverExpectationsMap" -> leftoverExpectationsMap,
      "failedBatches" -> failedBatches).toString
  }

}
