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

import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.stores.MetricRow.ResultCountStore
import de.awagen.kolibri.datatypes.values.{BiRunningValue, MetricValue}

object MetricRow {

  class ResultCountStore(successes: Int, fails: Int) extends KolibriSerializable{
    private[this] var successCounter: Int = successes
    private[this] var failCounter: Int = fails

    def incrementSuccessCount(): Unit = successCounter += 1
    def incrementFailCount(): Unit = failCounter += 1

    def successCount: Int = successCounter
    def failCount: Int = failCounter

    override def equals(obj: Any): Boolean = {
      if (!obj.isInstanceOf[ResultCountStore]) false
      else {
        val other = obj.asInstanceOf[ResultCountStore]
        this.successCount == other.successCount && this.failCount == other.failCount
      }
    }

    override def hashCode(): Int = {
      var hash = 7
      hash = 31 * hash + successCount
      hash = 31 * hash + failCount
      hash
    }

    override def toString: String = s"ResultCountStore($successCount, $failCount)"
  }

  def empty: MetricRow = new MetricRow(new ResultCountStore(0, 0), Map.empty[String, Seq[String]], Map.empty[String, MetricValue[Any]])

  def emptyForParams(params: Map[String, Seq[String]]): MetricRow = new MetricRow(new ResultCountStore(0, 0), params, Map.empty[String, MetricValue[Any]])

  def isSuccessSample(metrics: MetricValue[Any]*): Boolean = {
    metrics.exists(x => x.biValue.value2.numSamples > 0)
  }

  def metricRow(metrics: MetricValue[Any]*): MetricRow = {
    var store = empty
    store = store.addFullMetricsSampleAndIncreaseSampleCount(metrics: _*)
    if (isSuccessSample(metrics:_*)) store.countStore.incrementSuccessCount()
    else store.countStore.incrementFailCount()
    store
  }

}


/**
  * single metric row, where each row is identified by set of parameters and metric values that hold for the parameters
  *
  * @param params  - Map with key = parameter name, value = sequence of values (assuming a single parameter could have
  *                multiple values)
  * @param metrics - Map with key = metric name and value = MetricValue[Any], describing the actual value that might
  *                be generated from many samples and error types along with the error counts
  */
case class MetricRow(countStore: ResultCountStore, params: Map[String, Seq[String]], metrics: Map[String, MetricValue[Any]]) extends MetricRecord[String, Any] {

  def weighted(weight: Double): MetricRow = {
    val map: Map[String, MetricValue[Any]] = metrics.map(x => {
      (x._1, MetricValue(x._2.name, BiRunningValue(x._2.biValue.value1, x._2.biValue.value2.weighted(weight))))
    })
    MetricRow(new ResultCountStore(countStore.successCount, countStore.failCount), params, map)
  }

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

  override def metricValues: Seq[MetricValue[Any]] = metrics.values.toSeq

  def containsMetric(key: String): Boolean = metrics.keys.toSeq.contains(key)

  override def getMetricsValue(key: String): Option[MetricValue[Any]] = metrics.get(key)

  override def addMetricDontChangeCountStore[T >: Any](addMetric: MetricValue[T]): MetricRow = {
    val currentMetricState: MetricValue[Any] = metrics.getOrElse(addMetric.name, addMetric.emptyCopy())
    val updatedMetricState = MetricValue[Any](addMetric.name, currentMetricState.biValue.add(addMetric.biValue))
    MetricRow(countStore, params, metrics + (addMetric.name -> updatedMetricState))
  }

  override def addFullMetricsSampleAndIncreaseSampleCount[T >: Any](newMetrics: MetricValue[T]*): MetricRow = {
    var result: MetricRow = this
    newMetrics.foreach(x => result = result.addMetricDontChangeCountStore(x))
    if (MetricRow.isSuccessSample(newMetrics:_*)) countStore.incrementSuccessCount()
    else countStore.incrementFailCount()
    result
  }

  override def addRecordAndIncreaseSampleCount[T >: Any](record: MetricRecord[String, T]): MetricRow = {
    var result = this
    record.metricValues.foreach(x => result = result.addMetricDontChangeCountStore(x))
    if (MetricRow.isSuccessSample(record.metricValues:_*)) countStore.incrementSuccessCount()
    else countStore.incrementFailCount()
    result
  }

}
