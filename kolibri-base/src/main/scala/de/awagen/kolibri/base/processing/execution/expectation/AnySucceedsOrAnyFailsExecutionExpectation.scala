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

object AnySucceedsOrAnyFailsExecutionExpectation {

  def empty: AnySucceedsOrAnyFailsExecutionExpectation = AnySucceedsOrAnyFailsExecutionExpectation(Seq.empty)

}


/**
  * Fails if any fails and succeds if any succeeds without failed expectation
  * @param expectations
  */
case class AnySucceedsOrAnyFailsExecutionExpectation(expectations: Seq[ExecutionExpectation]) extends ExecutionExpectation {
  override def failed: Boolean = !succeeded && expectations.count(x => x.failed) > 0

  override def failedWhenMetExpectations: Seq[Expectation[Any]] = expectations.flatMap(x => x.failedWhenMetExpectations)

  override def successWhenMetExpectations: Seq[Expectation[Any]] = expectations.flatMap(x => x.successWhenMetExpectations)

  override def failedExpectations: Seq[Expectation[Any]] = expectations.flatMap(x => x.failedExpectations)

  override def succeededExpectations: Seq[Expectation[Any]] = expectations.flatMap(x => x.succeededExpectations)

  override def deepCopy: ExecutionExpectation = AnySucceedsOrAnyFailsExecutionExpectation(expectations.map(x => x.deepCopy))

  override def init: Unit = expectations.foreach(x => x.init)

  override def succeeded: Boolean = expectations.exists(x => x.succeeded)

  override def statusDesc: String = expectations.map(x => x.toString).mkString("\n")

  override def accept[TT >: Any](element: TT): Unit = {
    expectations.foreach(x => x.accept(element))
  }
}
