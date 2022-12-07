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

package de.awagen.kolibri.datatypes.metrics.aggregation

import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.values.{BiRunningValue, MetricValue, RunningValue}

object MetricsHelper {

  def createMetricsCalculationSuccess(name: String, score: Double): MetricValue[Double] = {
    MetricValue(name, BiRunningValue(value1 = RunningValue.calcErrorRunningValue(0, Map.empty),
      value2 = RunningValue.doubleAvgRunningValue(1.0, 1, score)))
  }

  def createMetricsCalculationFailure(name: String, fails: Seq[ComputeFailReason]): MetricValue[Double] = {
    MetricValue(name, BiRunningValue(value1 = RunningValue.calcErrorRunningValue(1, RunningValue.mapFromFailReasons(fails)),
      value2 = RunningValue.doubleAvgRunningValue(0.0, 0, 0.0)))
  }

  val metricsSuccess1: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics1", 0.2, 1.0)
  val metricsSuccess2: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics2", 0.4, 1.0)
  val metricsSuccess3: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics3", 0.1, 1.0)
  val metricsSuccess4: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics4", 0.3, 1.0)
  val metricsSuccess5: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics5", 0.6, 1.0)

  val metricsFailed1: MetricValue[Double] = MetricValue
    .createDoubleAvgFailSample("metrics1", Map(ComputeFailReason("ZERO_DENOMINATOR") -> 1, ComputeFailReason("NO_RESULTS") -> 1))
  val metricsFailed2: MetricValue[Double] = MetricValue.createDoubleAvgFailSample("metrics2", Map(ComputeFailReason("NO_RESULTS") -> 1))

  val metricRecord1: MetricRow = MetricRow.empty.addFullMetricsSampleAndIncreaseSampleCount(metricsSuccess1, metricsSuccess2)
  val metricRecord2: MetricRow = MetricRow.empty.addFullMetricsSampleAndIncreaseSampleCount(metricsSuccess3)
  val metricRecord3: MetricRow = MetricRow.empty.addFullMetricsSampleAndIncreaseSampleCount(metricsSuccess4)
  val metricRecord4: MetricRow = MetricRow.empty.addFullMetricsSampleAndIncreaseSampleCount(metricsSuccess5)

}
