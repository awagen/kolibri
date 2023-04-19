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


package de.awagen.kolibri.fleet.akka.usecase.searchopt.processing.plan

import de.awagen.kolibri.definitions.processing.execution.task
import de.awagen.kolibri.definitions.processing.execution.task.{SimpleAsyncTask, SimpleSyncTask, SyncTask, Task}
import de.awagen.kolibri.definitions.usecase.searchopt.domain.ExtTaskDataKeys
import de.awagen.kolibri.definitions.usecase.searchopt.domain.ExtTaskDataKeys.{JUDGEMENTS, JUDGEMENTS_FAILED, JUDGEMENT_PROVIDER, JUDGEMENT_PROVIDER_FAILED, PRODUCT_ID_RESULT}
import de.awagen.kolibri.definitions.usecase.searchopt.processing.plan.Functions
import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementProvider

import scala.concurrent.{ExecutionContext, Future}

/**
 * Given keys in TaskDataKeys, provide the Task sequence to be executed within the TaskExecution
 *
 * deprecated / incomplete due to calculation adjustments - the standard way to execute is the flow way, not the single Task composition
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
   * Async judgement provider retrieval task
   *
   * @param judgementSupplier
   * @param ec
   * @return
   */
  def judgementProviderGetTask(judgementSupplier: () => Future[JudgementProvider[Double]])
                              (implicit ec: ExecutionContext): SimpleAsyncTask[JudgementProvider[Double], JudgementProvider[Double]] = {
    task.SimpleAsyncTask[JudgementProvider[Double], JudgementProvider[Double]](
      prerequisites = Seq.empty,
      successKey = ExtTaskDataKeys.JUDGEMENT_PROVIDER,
      failKey = JUDGEMENT_PROVIDER_FAILED,
      futureFunc = _ => judgementSupplier(),
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
   * Generate full sequence of judgement generation and metrics calculation
   * based on initial data map containing only Seq of product ids (Seq[String])
   *
   * @param query
   * @param judgementProviderFutureSupplier
   * @param ec
   * @return
   */
  def judgementGenerationTaskSeq(query: String,
                                 judgementProviderFutureSupplier: () => Future[JudgementProvider[Double]])
                                (implicit ec: ExecutionContext): Seq[Task[_]] = {
    Seq(
      judgementProviderGetTask(judgementProviderFutureSupplier),
      judgementGenerationTask(query)
    )
  }

}
