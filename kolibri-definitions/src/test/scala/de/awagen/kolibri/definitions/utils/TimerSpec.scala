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

import de.awagen.kolibri.definitions.testclasses.UnitTestSpec

import scala.concurrent.duration.Duration

class TimerSpec extends UnitTestSpec {

  "Timer" must {
    "correctly calculate time taken by function and return duration with results" in {
      val result: (String, Duration) = Timer.timeAndReturnResult({
        Thread.sleep(100)
        "testResult"
      })
      result._1 mustBe "testResult"
      result._2.gt(Duration.fromNanos(Math.pow(10, 8))) mustBe true
      result._2.lt(Duration.fromNanos(Math.pow(10, 8)) * 1.02) mustBe true
    }

    "run function n times and return summed duration" in {
      val result: Duration = Timer.timeNExecutions({
        Thread.sleep(100)
        "testResult"
      }, 10)
      result.gt(Duration.fromNanos(Math.pow(10, 8)) * 10.0) mustBe true
      result.lt(Duration.fromNanos(Math.pow(10, 8)) * 11.0) mustBe true
    }
  }

}
