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

import de.awagen.kolibri.base.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.base.usecase.searchopt.domain.ExtTaskDataKeys.{JUDGEMENTS, JUDGEMENT_PROVIDER, PRODUCT_ID_RESULT}
import de.awagen.kolibri.base.usecase.searchopt.metrics.MetricsCalculation
import de.awagen.kolibri.base.usecase.searchopt.domain.ExtTaskFailType.{JudgementProviderMissing, JudgementsMissing, ProductIdsMissing}
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProvider
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.stores.MetricRow


object Functions {

  def dataToJudgementsFunc(provider: JudgementProvider[Double], query: String): TypeTaggedMap => Either[TaskFailType, Seq[Option[Double]]] = {
    data =>
      val productIds: Option[Seq[String]] = data.get(PRODUCT_ID_RESULT)
      if (productIds.isEmpty) Left(ProductIdsMissing)
      Right(provider.retrieveJudgements(query, productIds.get))
  }

  def judgementRetrievalFunc(query: String): TypeTaggedMap => Either[TaskFailType, Seq[Option[Double]]] = {
    data =>
      val productResultsOpt: Option[Seq[String]] = data.get(PRODUCT_ID_RESULT)
      val judgementProviderOpt: Option[JudgementProvider[Double]] = data.get(JUDGEMENT_PROVIDER)
      if (productResultsOpt.isEmpty) Left(ProductIdsMissing)
      else if (judgementProviderOpt.isEmpty) Left(JudgementProviderMissing)
      else Right(judgementProviderOpt.get.retrieveJudgements(query, productResultsOpt.get))
  }

  def judgementsToMetricsFunc(metricsCalculation: MetricsCalculation): TypeTaggedMap => Either[TaskFailType, MetricRow] = {
    data =>
      val judgementsOpt: Option[Seq[Option[Double]]] = data.get(JUDGEMENTS)
      if (judgementsOpt.isEmpty) Left(JudgementsMissing)
      val judgements: Seq[Option[Double]] = judgementsOpt.get
      Right(metricsCalculation.calculateAll(judgements))
  }

}
