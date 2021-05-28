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

import de.awagen.kolibri.base.usecase.searchopt.provider.{JudgementProvider, JudgementProviderFactory}
import de.awagen.kolibri.datatypes.stores.MetricRow

import scala.concurrent.{ExecutionContext, Future}

object MetricsCalcFunctions {

  // TODO: we might wanna load all prerequisites (e.g judgements) before we start executing a batch on a node, then calculating
  // results is straightforward
  def calc(providerFactory: JudgementProviderFactory[Double],
           query: String,
           products: Seq[String],
           metricsCalculation: MetricsCalculation)(implicit ec: ExecutionContext): Future[MetricRow] = {
    val judgementFuture: Future[JudgementProvider[Double]] = providerFactory.getJudgements.future
    judgementFuture.map(x => {
      val judgements: Seq[Option[Double]] = x.retrieveJudgements(query, products)
      metricsCalculation.calculateAll(judgements)
    })

  }

}
