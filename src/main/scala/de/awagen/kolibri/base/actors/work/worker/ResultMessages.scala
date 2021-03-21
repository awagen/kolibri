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
import de.awagen.kolibri.base.actors.work.worker.JobPartIdentifiers.JobPartIdentifier
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.Tags.Tag

object ResultMessages {

  trait ResultMessage extends KolibriSerializable {
    val partIdentifier: JobPartIdentifier
  }

  case class ResultEvent[T](result: T, partIdentifier: JobPartIdentifier, tags: Set[Tag]) extends ResultMessage

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
