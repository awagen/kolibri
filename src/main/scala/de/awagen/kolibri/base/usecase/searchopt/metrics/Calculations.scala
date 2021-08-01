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

import de.awagen.kolibri.base.config.AppConfig.logger
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Flows.throwableToMetricRowResponse
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProviderFactory
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.MetricRow

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}


/**
  * Holding distinct types of calculation definitions.
  */
object Calculations {

  trait Calculation[In, Out] extends KolibriSerializable {
    val name: String
    def apply(in: In)(implicit ec: ExecutionContext): Out
  }

  trait FutureCalculation[In, Out] extends Calculation[In, Future[Out]]

  case class JudgementBasedMetricsCalculation(name: String,
                                              judgementProviderFactory: JudgementProviderFactory[Double],
                                              metricsCalculation: MetricsCalculation,
                                              excludeParamsFromMetricRow: Seq[String]) extends FutureCalculation[(WeaklyTypedMap[String], RequestTemplate), MetricRow] {
    override def apply(msg: (WeaklyTypedMap[String], RequestTemplate))(implicit ec: ExecutionContext): Future[MetricRow] = {
      val productIdKey: String = "productIds"
      val productIds: Seq[String] = msg._1.get[Seq[String]](productIdKey).get
      judgementProviderFactory.getJudgements.future
        .map(y => {
          val judgements: Seq[Option[Double]] = y.retrieveJudgements(msg._2.parameters("q").head, productIds)
          logger.debug(s"retrieved judgements: $judgements")
          val metricRow: MetricRow = metricsCalculation.calculateAll(immutable.Map(msg._2.parameters.toSeq.filter(x => !excludeParamsFromMetricRow.contains(x._1)): _*), judgements)
          logger.debug(s"calculated metrics: $metricRow")
          metricRow
        })
        .recover(throwable => {
          logger.warn(s"failed retrieving judgements: $throwable")
          throwableToMetricRowResponse(throwable)
        })
    }
  }

}
