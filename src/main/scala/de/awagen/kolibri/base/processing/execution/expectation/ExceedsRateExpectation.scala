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


/**
  * Expectation regarding rate of receiving processed elements.
  * Succeeds if the time between received elements is greater than the given max time between elements and the initial
  * delay has passed
  *
  * @param initialDelayInMillis           - initial delay in which rate can be exceeded without this expectation suceeding
  * @param maxTimeBetweenElementsInMillis - maximal allowed time between received elements
  * @param acceptFunction                 - function determining whether an element is accepted. In resetting of passed time only
  *                                       accepted messages have an effect
  * @param numExpectedElements            - the number of expected elements. As soon as this is reached, the expectation will always fail,
  *                                       since there is no rate to control anymore
  */
case class ExceedsRateExpectation(initialDelayInMillis: Long,
                                  maxTimeBetweenElementsInMillis: Long,
                                  acceptFunction: PartialFunction[Any, Boolean],
                                  numExpectedElements: Int) extends Expectation[Any] {
  private[this] var startTimeInMillis: Long = _
  private[this] var lastElementReceiveTime: Long = _
  private[this] var acceptedElements: Int = _

  override def init: Unit = {
    startTimeInMillis = System.currentTimeMillis()
  }

  override def succeeded: Boolean = {
    if (acceptedElements >= numExpectedElements) false
    else if (startTimeInMillis - System.currentTimeMillis() < initialDelayInMillis) false
    else if (lastElementReceiveTime - System.currentTimeMillis() > maxTimeBetweenElementsInMillis) true
    else false
  }

  override def statusDesc: String = {
    Seq(
      s"start time (in ms): $startTimeInMillis",
      s"lastElementReceiveTime: $lastElementReceiveTime",
      s"acceptedElements: $acceptedElements",
      s"numExpectedElements: $numExpectedElements",
      s"failed: ${!succeeded}",
    ).mkString("\n")
  }

  override def accept[TT >: Any](element: TT): Unit = {
    if (acceptFunction.apply(element) && acceptedElements < numExpectedElements) {
      lastElementReceiveTime = System.currentTimeMillis()
      acceptedElements += 1
    }
  }

  def setStartTimeInMillis(timeInMillis: Long): Unit = {
    this.startTimeInMillis = timeInMillis
  }

  def setLastElementReceiveTime(timeInMillis: Long): Unit = {
    this.lastElementReceiveTime = timeInMillis
  }

  override def deepCopy: Expectation[Any] = {
    val copy: ExceedsRateExpectation = this.copy()
    copy.setLastElementReceiveTime(this.lastElementReceiveTime)
    copy.setStartTimeInMillis(this.startTimeInMillis)
    copy
  }
}