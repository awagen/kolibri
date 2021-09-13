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
package de.awagen.kolibri.datatypes.stores

import de.awagen.kolibri.datatypes.values.MetricValue

object MetricRow {

  def empty: MetricRow = new MetricRow(Map.empty[String, Seq[String]], Map.empty[String, MetricValue[Double]])

  def metricRow(metrics: MetricValue[Double]*): MetricRow = {
    var store = empty
    store = store.addMetrics(metrics: _*)
    store
  }

}


/**
  * single metric row, where each row is identified by set of parameters and metric values that hold for the parameters
  *
  * @param params  - Map with key = parameter name, value = sequence of values (assuming a single parameter could have
  *                multiple values)
  * @param metrics - Map with key = metric name and value = MetricValue[Double], describing the actual value that might
  *                be generated from many samples and error types along with the error counts
  */
case class MetricRow(params: Map[String, Seq[String]], metrics: Map[String, MetricValue[Double]]) extends MetricRecord[String, Double] {

  def successCountForMetric(metricName: String): Int = {
    metrics.get(metricName).map(value => value.biValue.value2.numSamples).getOrElse(0)
  }

  def errorCountForMetric(metricName: String): Int = {
    metrics.get(metricName).map(value => value.biValue.value1.numSamples).getOrElse(0)
  }

  def weightForMetric(metricName: String): Double = {
    metrics.get(metricName).map(value => value.biValue.value2.weight).getOrElse(0.0)
  }

  override def metricNames: Seq[String] = metrics.keys.toSeq

  override def metricValues: Seq[MetricValue[Double]] = metrics.values.toSeq

  def containsMetric(key: String): Boolean = metrics.keys.toSeq.contains(key)

  override def getMetricsValue(key: String): Option[MetricValue[Double]] = metrics.get(key)

  override def addMetric(addMetric: MetricValue[Double]): MetricRow = {
    val currentMetricState: MetricValue[Double] = metrics.getOrElse(addMetric.name, MetricValue.createEmptyAveragingMetricValue(addMetric.name))
    val updatedMetricState = MetricValue[Double](addMetric.name, currentMetricState.biValue.add(addMetric.biValue))
    MetricRow(params, metrics + (addMetric.name -> updatedMetricState))
  }

  override def addMetrics(newMetrics: MetricValue[Double]*): MetricRow = {
    var result: MetricRow = this
    newMetrics.foreach(x => result = result.addMetric(x))
    result
  }

  override def addRecord(record: MetricRecord[String, Double]): MetricRow = {
    var result = this
    record.metricValues.foreach(x => result = result.addMetric(x))
    result
  }

}
