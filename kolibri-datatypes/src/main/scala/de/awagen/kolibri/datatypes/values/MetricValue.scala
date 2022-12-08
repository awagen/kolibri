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
import de.awagen.kolibri.datatypes.values.RunningValue._


object MetricValue {

  def createMetricValue[T](metricName: String,
                           failCount: Int,
                           failMap: Map[ComputeFailReason, Int],
                           runningValue: RunningValue[T]): MetricValue[T] = {
    MetricValue(
      name = metricName,
      BiRunningValue(value1 = calcErrorRunningValue(failCount, failMap), value2 = runningValue))
  }

  def createSingleFailSample[T](metricName: String,
                                failMap: Map[ComputeFailReason, Int],
                                runningValue: RunningValue[T],
                               ): MetricValue[T] = {
    createMetricValue(metricName, 1, failMap, runningValue)
  }

  def createNoFailSample[T](metricName: String, runningValue: RunningValue[T]): MetricValue[T] = {
    createMetricValue(metricName, 0, Map.empty, runningValue)
  }

  def createMapValueCountFailSample[T](metricName: String, failMap: Map[ComputeFailReason, Int], runningValue: RunningValue[Map[T, Double]]): MetricValue[Map[T, Double]] = {
    createSingleFailSample(metricName, failMap, runningValue)
  }

  def createMapAvgValueSuccessSample[T](metricName: String, value: Map[T, Double], weight: Double): MetricValue[Map[T, Double]] = {
    createNoFailSample(metricName, mapValueAvgRunningValue(weight, 1, value))
  }

  def createMapSumValueSuccessSample[T](metricName: String, value: Map[T, Double], weight: Double): MetricValue[Map[T, Double]] = {
    createNoFailSample(metricName, mapValueWeightedSumRunningValue(weight, 1, value))
  }

  def createNestedMapSumValueSuccessSample[U, V](metricName: String, value: Map[U, Map[V, Double]], weight: Double): MetricValue[Map[U, Map[V, Double]]] = {
    createNoFailSample(metricName, nestedMapValueWeightedSumUpRunningValue(weight, 1, value))
  }

  def createNestedMapSumValueFailSample[U, V](metricName: String, failMap: Map[ComputeFailReason, Int]): MetricValue[Map[U, Map[V, Double]]] = {
    createSingleFailSample(metricName, failMap, nestedMapValueWeightedSumUpRunningValue(0.0, 0, Map.empty))
  }

  def createDoubleAvgFailSample(metricName: String, failMap: Map[ComputeFailReason, Int]): MetricValue[Double] = {
    createSingleFailSample[Double](metricName, failMap, doubleAvgRunningValue(0.0, 0, 0.0))
  }

  def createDoubleAvgSuccessSample(metricName: String, value: Double, weight: Double): MetricValue[Double] = {
    createNoFailSample(metricName, doubleAvgRunningValue(weight, 1, value))
  }

  def createDoubleEmptyAveragingMetricValue(name: String): MetricValue[Double] = {
    createNoFailSample(name, doubleAvgRunningValue(0.0, 0, 0.0))
  }

}

/**
  * Simple container keeping state with BiRunningValue that keeps track of occurring error types and the
  * respective counts and some aggregated value type to keep track of the successful computations aggregated in the
  * MetricValue
  *
  * @param name    - the name of the metric value
  * @param biValue - BiRunningValue keeping track of error types, their occurrence counts, the actual value made of
  *                successful computations. Note that BiRunningValue keeps track of how many samples went into either by
  *                utilizing AggregateValue[Map[ComputeFailReason, Int]] and AggregateValue[A]. Thus note that the
  *                sum of the counts in the error type map will always be >= the actual count of the sampels that went
  *                into it
  * @param aggregateValueInitSupplier -
  * @tparam A - type of the aggregated value made of successful computations
  */
case class MetricValue[+A](name: String,
                           biValue: BiRunningValue[Map[ComputeFailReason, Int], A]) {

  def emptyCopy(): MetricValue[A] = {
    this.copy(name: String, BiRunningValue[Map[ComputeFailReason, Int], A](
      value1 = calcErrorRunningValue(0, Map.empty),
      value2 = biValue.value2.emptyCopy()
    ))
  }

}