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


package de.awagen.kolibri.base.processing.execution.functions

import de.awagen.kolibri.base.processing.execution.functions.AnalyzeFunctions.{KeepNBestAndKWorst, QueryParamValue}
import de.awagen.kolibri.base.testclasses.UnitTestSpec

class AnalyzeFunctionsSpec extends UnitTestSpec {

  "KeepNBestAndKWorst" must {

    "keep record of best and worst" in {
      // given
      val record = KeepNBestAndKWorst(5, 4, x => f"$x%1.1f")
      // when
      Range(0, 100).map(x => QueryParamValue(s"q$x", Map("p1" -> Seq("v1")), x * 0.1)).foreach(
        value => record.accept(value)
      )
      val result: Map[String, Map[Map[String, Seq[String]], Seq[(String, String)]]] = record.result
      // then
      result("highest").view.mapValues(x => x.map(y => (y._1, y._2))).toMap mustBe Map(Map("p1" -> Seq("v1")) -> Seq(("q99", "9.9"), ("q98", "9.8"), ("q97", "9.7"), ("q96", "9.6"), ("q95", "9.5")))
      result("lowest").view.mapValues(x => x.map(y => (y._1, y._2))).toMap mustBe Map(Map("p1" -> Seq("v1")) -> Seq(("q0", "0.0"), ("q1", "0.1"), ("q2", "0.2"), ("q3", "0.3")))
    }

  }

}
