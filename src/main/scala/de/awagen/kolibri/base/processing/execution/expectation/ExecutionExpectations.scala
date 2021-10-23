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


package de.awagen.kolibri.base.processing.execution.expectation

import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.FinishedJobEvent
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationStateWithData, AggregationStateWithoutData, ProcessingResult}
import de.awagen.kolibri.base.processing.execution.expectation.Expectation.SuccessAndErrorCounts
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.FiniteDuration

object ExecutionExpectations {

  private val log: Logger = LoggerFactory.getLogger(this.getClass)

  val FINISH_RESPONSE_KEY: String = "finishResponse"

  // we only expect one FinishedJobEvent per job
  // StopExpectation met if an FinishedJobEvent has FAILURE result type, ignores all other messages
  // except FinishedJobEvent; also sets a TimeoutExpectation to abort
  // jobs on exceeding it
  def createSupervisorJobExecutionExpectation(allowedDuration: FiniteDuration): ExecutionExpectation = {
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


  /**
    *
    * @param numberBatches - number of batches
    * @param maxProcessDuration - maximal allowed process duration (for whole job)
    * @param expectResultsFromBatchCalculations - boolean to indicate whether we expect to receive results with data or notifications of completion suffice
    * @return
    */
  def jobExpectation(numberBatches: Int, maxProcessDuration: FiniteDuration, expectResultsFromBatchCalculations: Boolean): ExecutionExpectation = {
    val failExpectations: Seq[Expectation[Any]] = Seq(TimeExpectation(maxProcessDuration))
    BaseExecutionExpectation(
      fulfillAllForSuccess = Seq(
        ClassifyingCountExpectation(Map(FINISH_RESPONSE_KEY -> {
          case _: AggregationStateWithData[_] =>
            if (!expectResultsFromBatchCalculations) {
              log.warn(s"received AggregationState with data but expectResultsFromBatchCalculations=$expectResultsFromBatchCalculations")
            }
            true
          case _: AggregationStateWithoutData[_] =>
            if (expectResultsFromBatchCalculations) {
              log.warn(s"received AggregationState without data but expectResultsFromBatchCalculations=$expectResultsFromBatchCalculations")
              false
            }
            else true
          case _ => false
        }), Map(FINISH_RESPONSE_KEY -> numberBatches))
      ),
      fulfillAnyForFail = failExpectations)
  }

}
