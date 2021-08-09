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

package de.awagen.kolibri.datatypes.values

import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.values.RunningValue.{calcErrorRunningValue, doubleAvgRunningValue}


object MetricValue {

  def createAvgFailSample(metricName: String, failMap: Map[ComputeFailReason, Int]): MetricValue[Double] = {
    MetricValue(name = metricName,
      BiRunningValue(value1 = calcErrorRunningValue(1, failMap),
        value2 = doubleAvgRunningValue(0, 0.0)))
  }

  def createAvgSuccessSample(metricName: String, value: Double): MetricValue[Double] = {
    MetricValue(name = metricName,
      BiRunningValue(value1 = calcErrorRunningValue(0, Map.empty),
        value2 = doubleAvgRunningValue(1, value)))
  }

  def createEmptyAveragingMetricValue(name: String): MetricValue[Double] = {
    MetricValue(name = name, BiRunningValue(value1 = calcErrorRunningValue(0, Map.empty),
      value2 = doubleAvgRunningValue(0, 0.0)))
  }

}

/**
  * Simple container keeping state with BiRunningValue that keeps track of occurring error types and the
  * respective counts and some aggregated value type to keep track of the successful computations aggregated in the
  * MetricValue
  * @param name - the name of the metric value
  * @param biValue - BiRunningValue keeping track of error types, their occurrence counts, the actual value made of
  *                successful computations. Note that BiRunningValue keeps track of how many samples went into either by
  *                utilizing AggregateValue[Map[ComputeFailReason, Int]] and AggregateValue[A]. Thus note that the
  *                sum of the counts in the error type map will always be >= the actual count of the sampels that went
  *                into it
  * @tparam A - type of the aggregated value made of successful computations
  */
case class MetricValue[A](name: String, biValue: BiRunningValue[Map[ComputeFailReason, Int], A])
