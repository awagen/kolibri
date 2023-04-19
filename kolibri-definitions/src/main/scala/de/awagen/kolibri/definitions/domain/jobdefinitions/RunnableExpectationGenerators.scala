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

package de.awagen.kolibri.definitions.domain.jobdefinitions

import de.awagen.kolibri.definitions.processing.ProcessingMessages
import de.awagen.kolibri.definitions.processing.execution.expectation.Expectation.SuccessAndErrorCounts
import de.awagen.kolibri.definitions.processing.execution.expectation._
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1

import scala.concurrent.duration._


object RunnableExpectationGenerators extends Enumeration {
  type ExpectationGenerators = Val

  case class Val(elementCountToExpectationFunc: SerializableFunction1[Int, ExecutionExpectation]) extends super.Val

  val ONE_FOR_ONE: Val = Val(_ => BaseExecutionExpectation(
    fulfillAllForSuccess = Seq(ReceiveCountExpectation(Map(ProcessingMessages.Corn -> 1))),
    fulfillAnyForFail = Seq(StopExpectation(0, _ => SuccessAndErrorCounts(1, 0), _ => false),
      TimeExpectation(10 minutes))))


}
