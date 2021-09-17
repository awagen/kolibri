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

import de.awagen.kolibri.base.usecase.searchopt.metrics.JudgementValidation.JudgementValidation
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.values.{MetricValue, RunningValue}

import scala.collection.immutable


case class MetricsCalculation(metrics: Seq[Metric], judgementHandling: JudgementHandlingStrategy) {

  def validateAndReturnFailedJudgements(judgements: Seq[Option[Double]]): Seq[JudgementValidation] = {
    judgementHandling.validateAndReturnFailed(judgements)
  }

  def calculateMetric(metric: Metric, values: Seq[Option[Double]]): MetricValue[Double] = {
    val failedValidations: Seq[JudgementValidation] = validateAndReturnFailedJudgements(values)

    if (failedValidations.nonEmpty) {
      MetricValue.createAvgFailSample(metric.name, RunningValue.mapFromFailReasons(failedValidations.map(x => x.reason)))
    }
    else {
      val preparedValues: Seq[Double] = judgementHandling.extractValues(values)
      val result: Either[Seq[ComputeFailReason], Double] = metric.function.apply(preparedValues)
      result match {
        case Right(score) =>
          MetricValue.createAvgSuccessSample(metric.name, score, 1.0)
        case Left(failReasons) =>
          MetricValue.createAvgFailSample(metricName = metric.name, RunningValue.mapFromFailReasons(failReasons))
      }
    }
  }

  def calculateAll(params: immutable.Map[String, Seq[String]], values: Seq[Option[Double]]): MetricRow = {
    var metricRow = MetricRow.emptyForParams(params = params)
    metrics.foreach(x => metricRow = metricRow.addMetric(calculateMetric(x, values)))
    metricRow
  }

}
