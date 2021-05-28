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

package de.awagen.kolibri.base.usecase.searchopt.processing.plan

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.domain.TaskDataKeys.{METRICS, METRICS_FAILED, METRICS_PM}
import de.awagen.kolibri.base.processing.execution.task.{SimpleAsyncTask, SimpleSyncTask, SyncTask, Task}
import de.awagen.kolibri.base.usecase.searchopt.domain.ExtTaskDataKeys
import de.awagen.kolibri.base.usecase.searchopt.domain.ExtTaskDataKeys.{JUDGEMENTS, JUDGEMENTS_FAILED, JUDGEMENT_PROVIDER, JUDGEMENT_PROVIDER_FAILED, PRODUCT_ID_RESULT}
import de.awagen.kolibri.base.usecase.searchopt.metrics.MetricsCalculation
import de.awagen.kolibri.base.usecase.searchopt.provider.{JudgementProvider, JudgementProviderFactory}
import de.awagen.kolibri.datatypes.stores.MetricRow
import scala.concurrent.ExecutionContext


/**
  * Given keys in TaskDataKeys, provide the Task sequence to be executed within the TaskExecution
  */
object PlanProvider {


  /**
    * Generate judgements based on provided JudgementProvider
    *
    * @param provider
    * @param query
    * @return
    */
  def judgementGenerationTask(provider: JudgementProvider[Double], query: String): SyncTask[Seq[Option[Double]]] = SimpleSyncTask[Seq[Option[Double]]](
    Seq(PRODUCT_ID_RESULT),
    JUDGEMENTS,
    JUDGEMENTS_FAILED,
    Functions.dataToJudgementsFunc(provider, query))

  /**
    * assuming judgements are set in data (TypeTaggedMap),
    * calculate metrics given provided MetricsCalculation instance
    *
    * @param metricsCalculation
    * @return
    */
  def metricsCalculationTask(metricsCalculation: MetricsCalculation): SyncTask[ProcessingMessage[MetricRow]] = SimpleSyncTask[ProcessingMessage[MetricRow]](
    Seq(JUDGEMENTS),
    METRICS_PM,
    METRICS_FAILED,
    Functions.judgementsToMetricsFunc(metricsCalculation).andThen({
      case Left(e) => Left(e)
      case Right(e) => Right(Corn(e))
    })
  )

  /**
    * short execution plan assuming already existing JudgementProvider
    *
    * @param provider
    * @param metricsCalculation
    * @param query
    * @return
    */
  def resultEvaluationPlan(provider: JudgementProvider[Double], metricsCalculation: MetricsCalculation, query: String): Seq[SyncTask[_]] = Seq(
    judgementGenerationTask(provider, query),
    metricsCalculationTask(metricsCalculation)
  )

  /**
    * Async judgement provider retrieval task
    *
    * @param judgementProviderFactory
    * @param ec
    * @return
    */
  def judgementProviderGetTask(judgementProviderFactory: JudgementProviderFactory[Double])
                              (implicit ec: ExecutionContext): SimpleAsyncTask[JudgementProvider[Double], JudgementProvider[Double]] = {
    SimpleAsyncTask[JudgementProvider[Double], JudgementProvider[Double]](
      prerequisites = Seq.empty,
      successKey = ExtTaskDataKeys.JUDGEMENT_PROVIDER,
      failKey = JUDGEMENT_PROVIDER_FAILED,
      futureFunc = x => judgementProviderFactory.getJudgements.future,
      successHandler = (_, _) => (),
      failureHandler = _ => ())
  }

  /**
    * given udgement provider and the sequence of product ids together with query,
    * generate the list of judgements
    *
    * @param query
    * @return
    */
  def judgementGenerationTask(query: String): SimpleSyncTask[Seq[Option[Double]]] = {
    SimpleSyncTask[Seq[Option[Double]]](
      prerequisites = Seq(PRODUCT_ID_RESULT, JUDGEMENT_PROVIDER),
      successKey = JUDGEMENTS,
      failKey = JUDGEMENTS_FAILED,
      func = Functions.judgementRetrievalFunc(query))
  }

  /**
    * given judgements and MetricsCalculation instance, syncronous task to generate metrics
    *
    * @param metricsCalculation
    * @return
    */
  def judgementsToMetricsTask(metricsCalculation: MetricsCalculation): SimpleSyncTask[MetricRow] = {
    SimpleSyncTask[MetricRow](
      prerequisites = Seq(JUDGEMENTS),
      successKey = METRICS,
      failKey = METRICS_FAILED,
      func = Functions.judgementsToMetricsFunc(metricsCalculation))
  }

  /**
    * Generate full sequence of judgement generation and metrics calculation
    * based on initial data map containing only Seq of product ids (Seq[String])
    *
    * @param query
    * @param judgementProviderFactory
    * @param metricsCalculation
    * @param ec
    * @return
    */
  def metricsCalcuationTaskSeq(query: String, judgementProviderFactory: JudgementProviderFactory[Double], metricsCalculation: MetricsCalculation)
                              (implicit ec: ExecutionContext): Seq[Task[_]] = {
    Seq(
      judgementProviderGetTask(judgementProviderFactory),
      judgementGenerationTask(query),
      judgementsToMetricsTask(metricsCalculation)
    )
  }

}
