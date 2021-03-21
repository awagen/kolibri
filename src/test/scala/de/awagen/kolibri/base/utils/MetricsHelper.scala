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

package de.awagen.kolibri.base.utils

import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.values.{BiRunningValue, MetricValue, RunningValue}

object MetricsHelper {

  def createMetricsCalculationSuccess(name: String, score: Double): MetricValue[Double] = {
    MetricValue(name, BiRunningValue(value1 = RunningValue.calcErrorRunningValue(0, Map.empty),
      value2 = RunningValue.doubleAvgRunningValue(1, score)))
  }

  def createMetricsCalculationFailure(name: String, fails: Seq[ComputeFailReason]): MetricValue[Double] = {
    MetricValue(name, BiRunningValue(value1 = RunningValue.calcErrorRunningValue(1, RunningValue.mapFromFailReasons(fails)),
      value2 = RunningValue.doubleAvgRunningValue(0, 0.0)))
  }

}
