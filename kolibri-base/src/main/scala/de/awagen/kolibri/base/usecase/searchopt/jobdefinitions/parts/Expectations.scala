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


package de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{AggregationStateWithData, AggregationStateWithoutData, Corn}
import de.awagen.kolibri.base.processing.execution.expectation.Expectation.SuccessAndErrorCounts
import de.awagen.kolibri.base.processing.execution.expectation._
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation

import scala.concurrent.duration._
import scala.reflect.ClassTag

object Expectations {

  /**
    * Function taking number of overall element counts and returning ExecutionExpectation reflecting the passed criteria
    * on timeout, failure fraction. Note that the below always assumes that a Corn holds valid ("successfully generated")
    * data, while e.g BadCorn is supposed to be unsuccessful.
    */
  def expectationPerBatchSupplier[T <: AnyRef : ClassTag](timeout: FiniteDuration,
                                                          minOverallElementCount: Int = 10,
                                                          maxAllowedFailFraction: Float = 0.2F,
                                                          successAndErrorClassifier: Any => SuccessAndErrorCounts): Int => ExecutionExpectation = v1 => {
    AnySucceedsOrAnyFailsExecutionExpectation(
      Seq(
        // expectation for the AggregationState results to allow either aggregation
        // on single results or split and based on single subaggregations
        BaseExecutionExpectation(
          fulfillAllForSuccess = Seq(ElementCountingExpectation(
            countPerElementFunc = {
              case AggregationStateWithData(data: MetricAggregation[T], _, _, _) =>
                data.count
              case AggregationStateWithoutData(count: Int, _, _, _) =>
                count
              case Corn(e, _) if e.isInstanceOf[T] =>
                val successAndErrorCounts = successAndErrorClassifier.apply(e)
                if (successAndErrorCounts.successCount > 0) 1 else 0
              case _ => 0
            }, v1)
          ),
          fulfillAnyForFail = Seq(
            StopExpectation(
              v1,
              x => successAndErrorClassifier.apply(x),
              x => v1 > minOverallElementCount && x._2.toFloat / x._1 > maxAllowedFailFraction
            ),
            TimeExpectation(timeout))
        ))
    )
  }

}
