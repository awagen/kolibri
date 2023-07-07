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

import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.JudgementValidation.JudgementValidation
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.MetricsCalculation.{calculateJudgementMetrics, calculationResultToMetricValue}
import de.awagen.kolibri.definitions.usecase.searchopt.provider.{JudgementInfo, JudgementProvider}
import de.awagen.kolibri.datatypes.values.Calculations.{ComputeResult, ResultRecord}
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import de.awagen.kolibri.datatypes.values.{MetricValue, RunningValues}
import org.slf4j.LoggerFactory

import scala.collection.immutable


object MetricsCalculation {

  private[metrics] val logger = LoggerFactory.getLogger(MetricsCalculation.getClass)

  val AVAILABLE_JUDGEMENT_METRICS_SUB_RESULT_SIZES: Seq[Int] = Seq(2, 4, 8, 12, 24)
  val AVAILABLE_JUDGEMENT_METRICS: Seq[String] = AVAILABLE_JUDGEMENT_METRICS_SUB_RESULT_SIZES.map(k => s"J@$k")
  val AVAILABLE_JUDGEMENT_METRICS_NAME_TO_TYPE_MAPPING: Map[String, AggregationType.Val[Double]] =
    AVAILABLE_JUDGEMENT_METRICS.map(metricName => (metricName, AggregationType.DOUBLE_AVG)).toMap

  /**
   * Create for each k value a metric with name J@K and value equal to the count of available metrics
   * in the subsequence of first k elements.
   *
   * @param judgementsOptSeq - judgement values. If None then no judgement could be found for that position
   * @return
   */
  private[metrics] def calculateJudgementMetrics(judgementsOptSeq: Seq[Option[Double]]): Seq[ResultRecord[Double]] = {
    AVAILABLE_JUDGEMENT_METRICS_SUB_RESULT_SIZES.indices.map(
      index => {
        val k = AVAILABLE_JUDGEMENT_METRICS_SUB_RESULT_SIZES(index)
        val metricName = AVAILABLE_JUDGEMENT_METRICS(index)
        ResultRecord(metricName, Right(judgementsOptSeq.take(k).count(opt => opt.nonEmpty)))
      }
    )
  }

  def calculationResultToMetricValue(name: String, calculationResult: ComputeResult[Double]): MetricValue[Double] = {
    calculationResult match {
      case Right(score) =>
        MetricValue.createDoubleAvgSuccessSample(name, score, 1.0)
      case Left(failReasons) =>
        MetricValue.createDoubleAvgFailSample(metricName = name, RunningValues.mapFromFailReasons(failReasons))
    }
  }
}


  case class MetricsCalculation(metrics: Seq[Metric], judgementHandling: JudgementHandlingStrategy) {

  /**
   * Calculation of a single metric.
   * @param metric - definition of metric computation such as DCG, NDCG, ERR, PRECISION, RECALL
   * @param query - query for which to compute the metric
   * @param products - the sequence of retrieved product identifiers, which should be the same
   *                 as used in the judgements data
   * @param judgementProvider - provider of judgements (for specific query - product(s) and overall
   *                          best sorting as per judgement data.
   * @return ResultRecord[Double] of the computation
   */
  private[metrics] def calculateSingleMetric(metric: Metric,
                                             query: String,
                                             products: Seq[String],
                                             judgementProvider: JudgementProvider[Double]): ResultRecord[Double] = {
    val judgementInfo = JudgementInfo.create(query, products, judgementProvider, judgementHandling)

    val failedValidations: Seq[JudgementValidation] = judgementInfo.failedJudgementValidations
    if (failedValidations.nonEmpty) {
      ResultRecord(metric.name, Left(failedValidations.map(x => x.reason)))
    }
    else {
      val result: ComputeResult[Double] = metric.function.calc.apply(judgementInfo)
      ResultRecord(metric.name, result)
    }
  }

  def calculateAllAndReturnSingleResults(query: String, products: Seq[String], judgementProvider: JudgementProvider[Double]): Seq[ResultRecord[Double]] = {
    // calculate metrics regarding availability of judgements
    val judgementsOptSeq: Seq[Option[Double]] = judgementProvider.retrieveJudgements(query, products)
    val judgementMetrics: Seq[ResultRecord[Double]] = calculateJudgementMetrics(judgementsOptSeq)
    // calculate the actual metrics
    val resultMetrics: Seq[ResultRecord[Double]] = metrics.map(x => calculateSingleMetric(x, query, products, judgementProvider))
    judgementMetrics ++ resultMetrics
  }

  def calculateAllAndAddAllToMetricRow(params: immutable.Map[String, Seq[String]],
                                       query: String,
                                       products: Seq[String],
                                       judgementProvider: JudgementProvider[Double]): MetricRow = {
    val metricRow = MetricRow.emptyForParams(params = params)
    val allMetrics: Seq[MetricValue[Double]] = calculateAllAndReturnSingleResults(query, products, judgementProvider)
      .map(record => calculationResultToMetricValue(record.name, record.value))
    metricRow.addFullMetricsSampleAndIncreaseSampleCount(allMetrics: _*)
  }

}
