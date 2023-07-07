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

import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.values.{BiRunningValue, MetricValue, RunningValues}

object MetricsHelper {

  def createMetricsCalculationSuccess(name: String, score: Double): MetricValue[Double] = {
    MetricValue(name, BiRunningValue(value1 = RunningValues.calcErrorRunningValue(0, Map.empty),
      value2 = RunningValues.doubleAvgRunningValue(1.0, 1, score)))
  }

  def createMetricsCalculationFailure(name: String, fails: Seq[ComputeFailReason]): MetricValue[Double] = {
    MetricValue(name, BiRunningValue(value1 = RunningValues.calcErrorRunningValue(1, RunningValues.mapFromFailReasons(fails)),
      value2 = RunningValues.doubleAvgRunningValue(0.0, 0, 0.0)))
  }

}
