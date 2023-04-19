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

package de.awagen.kolibri.definitions.usecase.searchopt.metrics

object JudgementsMetrics extends Enumeration {
  type JudgementMetrics = Val
  case class Val(name: String, func: Seq[Option[Double]] => Double)

  val JM_MISSING_VALUES_FIRST_8: JudgementMetrics = Val("JM_MISSING_VALUES_FIRST_8", x => x.take(8).count(y => y.isEmpty))
  val JM_MISSING_VALUES_TOTAL: JudgementMetrics = Val("JM_MISSING_VALUES_TOTAL", x => x.count(y => y.isEmpty))
  val JM_RESULTS_TOTAL: JudgementMetrics = Val("JM_RESULTS_COUNT_TOTAL", x => x.size)
}
