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

package de.awagen.kolibri.definitions.utils

import scala.concurrent.duration.Duration

object Timer {

  def timeAndReturnResult[R](block: => R): (R, Duration) = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    (result, Duration.fromNanos(t1 - t0))
  }

  def timeNExecutions[R](block: => R, n: Int): Duration = {
    var total: Double = 0D
    for (_ <- 1 to n) {
      val t0 = System.nanoTime()
      block // call-by-name
      val t1 = System.nanoTime()
      total += (t1 - t0)
    }
    Duration.fromNanos(total)
  }
}
