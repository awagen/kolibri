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

case class ElementCountingExpectation(countPerElementFunc: SerializableFunction1[Any, Int],
                                      goalCount: Int) extends Expectation[Any] {

  private[this] var currentCount: Int = 0

  override def init: Unit = ()

  override def succeeded: Boolean = currentCount >= goalCount

  override def statusDesc: String = {
    Seq(
      s"current count: $currentCount",
      s"goal count: $goalCount",
    ).mkString("\n")
  }

  override def accept[TT >: Any](element: TT): Unit = {
    currentCount += countPerElementFunc.apply(element)
  }

  def setCurrentCount(count: Int): Unit = {
    this.currentCount = count
  }

  override def deepCopy: Expectation[Any] = {
    val copied = this.copy()
    copied.setCurrentCount(this.currentCount)
    copied
  }
}
