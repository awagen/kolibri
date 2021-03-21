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

import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1

/**
  * Stop expectation counting messages classified as error by the passed errorClassifier and
  * is fulfilled if passed stopCrtierion matches (takes overallElementCount and current errorCount to decide)
  *
  * @param overallElementCount                  : overall elements to process
  * @param errorClassifier                      : function taking in any message and checking if that matches an error
  * @param overallCountToFailCountFailCriterion : takes overallElementCount and errorCount to determine if stop criterion is fulfilled
  */
case class StopExpectation(overallElementCount: Int,
                           errorClassifier: SerializableFunction1[Any, Boolean],
                           overallCountToFailCountFailCriterion: SerializableFunction1[(Int, Int), Boolean]) extends Expectation[Any] {

  private[this] var errorCount: Int = 0

  override def init: Unit = ()

  private def setErrorCount(errorCount: Int): Unit = {
    this.errorCount = errorCount
  }

  override def accept[TT >: Any](element: TT): Unit = {
    if (errorClassifier.apply(element)) {
      errorCount += 1
    }
  }

  override def succeeded: Boolean = overallCountToFailCountFailCriterion.apply(overallElementCount, errorCount)

  override def statusDesc: String = Seq(
    s"overall element count: $overallElementCount",
    s"error count: $errorCount"
  ).mkString("\n")

  override def deepCopy: Expectation[Any] = {
    val copied = this.copy()
    copied.setErrorCount(this.errorCount)
    copied
  }
}
