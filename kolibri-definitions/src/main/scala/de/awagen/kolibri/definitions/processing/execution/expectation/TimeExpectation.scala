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


/**
  * Time expectation. Is fulfilled if given time since calling init() on the expectation has passed
  *
  * @param time
  */
case class TimeExpectation(time: Duration) extends Expectation[Any] {

  private[this] var startTimeInMillis: Option[Long] = None
  private[this] var stopTimeInMillis: Option[Long] = None
  private[this] var failed: Boolean = false

  override def init: Unit = {
    startTimeInMillis = Some(System.currentTimeMillis())
  }

  private def setStartTimeInMillis(startTimeInMillis: Option[Long]): Unit = {
    this.startTimeInMillis = startTimeInMillis
  }

  private def setStopTimeInMillis(stopTimeInMillis: Option[Long]): Unit = {
    this.stopTimeInMillis = stopTimeInMillis
  }

  private def setFailed(failed: Boolean): Unit = {
    this.failed = failed
  }

  def stop(): Unit = {
    val end: Long = System.currentTimeMillis()
    stopTimeInMillis = Some(end)
    if (Duration(end - startTimeInMillis.get, MILLISECONDS) >= time) failed = true
  }

  override def succeeded: Boolean = {
    if (failed) {
      return true
    }
    if (stopTimeInMillis.nonEmpty && !failed) return false
    val currentTime: Long = System.currentTimeMillis()
    val fulfilled = startTimeInMillis.nonEmpty && Duration(currentTime - startTimeInMillis.get, MILLISECONDS) >= time
    if (fulfilled) {
      stopTimeInMillis = Some(currentTime)
      failed = true
      return true
    }
    false
  }

  override def statusDesc: String = {
    Seq(
      s"start time (in ms): $startTimeInMillis",
      s"stopTimeInMillis: $stopTimeInMillis",
      s"failed: $failed",
    ).mkString("\n")
  }

  override def accept[TT >: Any](element: TT): Unit = ()

  override def deepCopy: Expectation[Any] = {
    val copied = this.copy()
    copied.setStartTimeInMillis(this.startTimeInMillis)
    copied.setStopTimeInMillis(this.stopTimeInMillis)
    copied.setFailed(this.failed)
    copied
  }
}
