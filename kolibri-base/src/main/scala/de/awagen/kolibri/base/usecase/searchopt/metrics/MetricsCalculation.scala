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

import de.awagen.kolibri.base.io.json.MetricFunctionJsonProtocol.MetricType
import de.awagen.kolibri.base.usecase.searchopt.metrics.JudgementValidation.JudgementValidation
import de.awagen.kolibri.base.usecase.searchopt.metrics.MetricsCalculation.calculationResultToMetricValue
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProvider
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.values.Calculations.{ComputeResult, ResultRecord}
import de.awagen.kolibri.datatypes.values.{MetricValue, RunningValues}
import org.slf4j.LoggerFactory

import scala.collection.immutable


object MetricsCalculation {

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

  val AVAILABLE_JUDGEMENT_METRICS_SUB_RESULT_SIZES: Seq[Int] = Seq(2, 4, 8, 12, 24)
  private[this] val logger = LoggerFactory.getLogger(MetricsCalculation.getClass)

  private[metrics] def validateAndReturnFailedJudgements(judgements: Seq[Option[Double]]): Seq[JudgementValidation] = {
    judgementHandling.validateAndReturnFailed(judgements)
  }

  private[metrics] def calculateSingleMetric(metric: Metric,
                                             query: String,
                                             products: Seq[String],
                                             judgementProvider: JudgementProvider[Double]): ResultRecord[Double] = {
    val values = judgementProvider.retrieveJudgements(query, products)
    val failedValidations: Seq[JudgementValidation] = validateAndReturnFailedJudgements(values)
    if (failedValidations.nonEmpty) {
      ResultRecord(metric.name, Left(failedValidations.map(x => x.reason)))
    }
    else {
      val preparedValues: Seq[Double] = judgementHandling.extractValues(values)
      val result: ComputeResult[Double] = metric.function.metricType match {
        // To have a constant normalization factor independent of the
        // retrieved result, we retrieve the ideal dcg for the query and
        // then calculate the dcg for the current result. Then NDCG is calculated
        // from both values (the standalone ndcg metric would just look at the
        // retrieved / passed current result and compare with the ideal sorting
        // of that sequence, thus we are handling this separately here)
        case MetricType.NDCG =>
          val idealDCG: ComputeResult[Double] = judgementProvider.getIdealDCGForTerm(query, metric.function.k) match {
            case Left(err) =>
              logger.warn("Trying to retrieve ideal dcg from judgement provider failed, need to calculate", err)
              val topKJudgements = judgementProvider.retrieveSortedJudgementsForTerm(query, metric.function.k)
              IRMetricFunctions.dcgAtK(metric.function.k)(topKJudgements)
            case e@Right(_) =>
              e
          }
          val currentDCG: ComputeResult[Double] = IRMetricFunctions.dcgAtK(metric.function.k).apply(preparedValues)
          (idealDCG, currentDCG) match {
            case (Right(ideal), Right(current)) =>
              if (ideal == 0.0) Left(Seq(ComputeFailReason("IdealDCG was 0.0")))
              else Right(current / ideal)
            case (a1, a2) =>
              val combinedFailReasons: Set[ComputeFailReason] = (a1.swap.getOrElse(Seq.empty) ++ a2.swap.getOrElse(Seq.empty)).toSet
              Left(combinedFailReasons.toSeq)
          }
        case _ =>
          metric.function.calc.apply(preparedValues)
      }
      ResultRecord(metric.name, result)
    }
  }

  /**
   * Create for each k value a metric with name J@K and value equal to the count of available metrics
   * in the subsequence of first k elements.
   *
   * @param judgementsOptSeq - judgement values. If None then no judgement could be found for that position
   * @return
   */
  private[metrics] def calculateJudgementMetrics(judgementsOptSeq: Seq[Option[Double]]): Seq[ResultRecord[Double]] = {
    AVAILABLE_JUDGEMENT_METRICS_SUB_RESULT_SIZES.map(
      k => ResultRecord(s"J@$k", Right(judgementsOptSeq.take(k).count(opt => opt.nonEmpty)))
    )
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
