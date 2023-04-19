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


package de.awagen.kolibri.fleet.akka.execution.expectation

import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingResult
import de.awagen.kolibri.definitions.processing.execution.expectation.ExecutionExpectations.FINISH_RESPONSE_KEY
import de.awagen.kolibri.definitions.processing.execution.expectation.Expectation.SuccessAndErrorCounts
import de.awagen.kolibri.definitions.processing.execution.expectation._
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor.FinishedJobEvent

import scala.concurrent.duration.FiniteDuration

object ExecutionExpectations {

  // we only expect one FinishedJobEvent per job
  // StopExpectation met if an FinishedJobEvent has FAILURE result type, ignores all other messages
  // except FinishedJobEvent; also sets a TimeoutExpectation to abort
  // jobs on exceeding it
  def finishedJobExecutionExpectation(allowedDuration: FiniteDuration): ExecutionExpectation = {
    BaseExecutionExpectation(
      fulfillAllForSuccess = Seq(ClassifyingCountExpectation(Map(FINISH_RESPONSE_KEY -> {
        case e: FinishedJobEvent if e.jobStatusInfo.resultSummary.result == ProcessingResult.SUCCESS => true
        case _ => false
      }), Map(FINISH_RESPONSE_KEY -> 1))),
      fulfillAnyForFail = Seq(
        StopExpectation(
          overallElementCount = 1,
          errorClassifier = {
            case e: FinishedJobEvent if e.jobStatusInfo.resultSummary.result == ProcessingResult.SUCCESS => SuccessAndErrorCounts(1, 0)
            case e: FinishedJobEvent if e.jobStatusInfo.resultSummary.result == ProcessingResult.FAILURE => SuccessAndErrorCounts(0, 1)
            case _ => SuccessAndErrorCounts(0, 0)
          },
          overallCountToFailCountFailCriterion = new SerializableFunction1[(Int, Int), Boolean] {
            override def apply(v1: (Int, Int)): Boolean = v1._2 > 0
          }),
        TimeExpectation(allowedDuration))
    )
  }

}
