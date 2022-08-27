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

package de.awagen.kolibri.base.usecase.searchopt.metrics

import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.{ComputeResult, ResultRecord}
import de.awagen.kolibri.base.usecase.searchopt.metrics.JudgementValidation.JudgementValidation
import de.awagen.kolibri.base.usecase.searchopt.metrics.MetricsCalculation.calculationResultToMetricValue
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.values.{MetricValue, RunningValue}

import scala.collection.immutable


object MetricsCalculation {

  def calculationResultToMetricValue(name: String, calculationResult: ComputeResult[Double]): MetricValue[Double] = {
    calculationResult match {
      case Right(score) =>
        MetricValue.createAvgSuccessSample(name, score, 1.0)
      case Left(failReasons) =>
        MetricValue.createAvgFailSample(metricName = name, RunningValue.mapFromFailReasons(failReasons))
    }
  }

}

case class MetricsCalculation(metrics: Seq[Metric], judgementHandling: JudgementHandlingStrategy) {

  def validateAndReturnFailedJudgements(judgements: Seq[Option[Double]]): Seq[JudgementValidation] = {
    judgementHandling.validateAndReturnFailed(judgements)
  }

  def calculateAndReturnCalculationResult(metric: Metric, values: Seq[Option[Double]]): ResultRecord[Double] = {
    val failedValidations: Seq[JudgementValidation] = validateAndReturnFailedJudgements(values)

    if (failedValidations.nonEmpty) {
      ResultRecord(metric.name, Left(failedValidations.map(x => x.reason)))
    }
    else {
      val preparedValues: Seq[Double] = judgementHandling.extractValues(values)
      ResultRecord(metric.name, metric.function.apply(preparedValues))
    }
  }

  def calculateMetric(metric: Metric, values: Seq[Option[Double]]): MetricValue[Double] = {
    val result: ResultRecord[Double] = calculateAndReturnCalculationResult(metric, values)
    calculationResultToMetricValue(metric.name, result.value)
  }

  def calculateAllAndReturnSingleResults(values: Seq[Option[Double]]): Seq[ResultRecord[Double]] = {
    metrics.map(x => calculateAndReturnCalculationResult(x, values))
  }

  def calculateAllAndAddAllToMetricRow(params: immutable.Map[String, Seq[String]], values: Seq[Option[Double]]): MetricRow = {
    val metricRow = MetricRow.emptyForParams(params = params)
    val allMetrics: Seq[MetricValue[Double]] = metrics.map(x => calculateMetric(x, values))
    metricRow.addFullMetricsSampleAndIncreaseSampleCount(allMetrics: _*)
  }

}
