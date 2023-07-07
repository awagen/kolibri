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

package de.awagen.kolibri.definitions.processing.execution.expectation

import scala.concurrent.duration._

object BaseExecutionExpectation {

  def empty(): BaseExecutionExpectation = {
    BaseExecutionExpectation(
      Seq(ReceiveCountExpectation(Map.empty)),
      Seq(TimeExpectation(100 days)))
  }

}

case class BaseExecutionExpectation(fulfillAllForSuccess: Seq[Expectation[Any]],
                                    fulfillAnyForFail: Seq[Expectation[Any]]) extends ExecutionExpectation {
  val allExpectations: Seq[Expectation[Any]] = fulfillAllForSuccess ++ fulfillAnyForFail

  override def init: Unit = allExpectations.foreach(x => x.init)

  override def accept[U >: Any](element: U): Unit = allExpectations.foreach(x => x.accept(element))

  override def failed: Boolean = !succeeded && fulfillAnyForFail.exists(x => x.succeeded)

  override def succeeded: Boolean = fulfillAllForSuccess.forall(x => x.succeeded)

  override def failedWhenMetExpectations: Seq[Expectation[Any]] = fulfillAnyForFail

  override def successWhenMetExpectations: Seq[Expectation[Any]] = fulfillAllForSuccess

  override def failedExpectations: Seq[Expectation[Any]] = {
    if (succeeded) return Seq.empty
    fulfillAnyForFail.filter(x => x.succeeded)
  }

  override def succeededExpectations: Seq[Expectation[Any]] = {
    fulfillAllForSuccess.filter(x => x.succeeded)
  }

  override def statusDesc: String = {
    val didFail = failedExpectations
    val didSucceed = succeededExpectations
    val failDesc: Seq[String] = didFail.map(x => s"FAILED:\n${x.statusDesc}")
    val successDesc: Seq[String] = didSucceed.map(x => s"SUCCEEDED:\n${x.statusDesc}")
    s"${(failDesc ++ successDesc).mkString("\n\n")}"
  }

  override def deepCopy: ExecutionExpectation = BaseExecutionExpectation(
    fulfillAllForSuccess = fulfillAllForSuccess.map(x => x.deepCopy),
    fulfillAnyForFail = fulfillAnyForFail.map(x => x.deepCopy)
  )
}
