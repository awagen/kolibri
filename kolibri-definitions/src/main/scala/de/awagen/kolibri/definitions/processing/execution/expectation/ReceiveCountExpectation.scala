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

import scala.collection.mutable

case class ReceiveCountExpectation(msgExpectations: Map[Any, Int]) extends Expectation[Any] {
  private[this] var currentState: mutable.Map[Any, Int] = collection.mutable.Map(msgExpectations.map(x => (x._1, 0)).toSeq: _*)
  private[this] var unfulfilledKeys: Seq[Any] = msgExpectations.keys.filter(x => msgExpectations(x) > 0).toSeq

  override def accept[TT >: Any](element: TT): Unit = {
    if (unfulfilledKeys.isEmpty) {
      return
    }
    currentState.get(element).foreach(x => {
      val new_value = x + 1
      currentState(element) = new_value
      if (new_value >= msgExpectations(element)) {
        unfulfilledKeys = unfulfilledKeys.filter(y => y != element)
      }
    })
  }

  private def setCurrentState(currentState: Map[Any, Int]): Unit = {
    this.currentState = mutable.Map(currentState.toSeq: _*)
  }

  private def setUnfulfilledKeys(keys: Seq[Any]): Unit = {
    this.unfulfilledKeys = keys
  }

  override def succeeded: Boolean = unfulfilledKeys.isEmpty

  override def init: Unit = ()

  override def statusDesc: String = Seq(
    s"unfulfilled keys: $unfulfilledKeys",
    s"currentState: $currentState",
    s"msgExpectations: $msgExpectations"
  ).mkString("\n")

  override def deepCopy: Expectation[Any] = {
    val copied = this.copy()
    copied.setCurrentState(Map(this.currentState.toSeq: _*))
    copied.setUnfulfilledKeys(this.unfulfilledKeys)
    copied
  }
}
