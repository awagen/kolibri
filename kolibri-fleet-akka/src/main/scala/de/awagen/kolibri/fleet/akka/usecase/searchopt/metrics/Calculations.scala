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


package de.awagen.kolibri.fleet.akka.usecase.searchopt.metrics

import de.awagen.kolibri.base.directives.{Resource, RetrievalDirective}
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.resources.RetrievalError
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys.REQUEST_TEMPLATE_STORAGE_KEY
import de.awagen.kolibri.base.usecase.searchopt.metrics.MetricsCalculation
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProvider
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.values.Calculations.{Calculation, ResultRecord}
import de.awagen.kolibri.fleet.akka.cluster.ClusterNodeObj

object Calculations {

  /**
   * Similar to JudgementBasedMetricsCalculation, yet here this uses judgements as resource, e.g
   * the set of judgements need to be pre-loaded as node resource.
   * One purpose is to unify all calculation definitions purely based on either fields created by result parsing
   * or from node resources.
   * @param productIdsKey - the key value used to store the productIds in the WeaklyTypedMap[String]
   * @param queryParamName - name of the parameter used in requests as query parameter
   * @param judgementsResource - the resource identifier for the judgements map
   * @param metricsCalculation - definition of which metrics to calculate
   */
  case class JudgementsFromResourceIRMetricsCalculations(productIdsKey: String,
                                                         queryParamName: String,
                                                         judgementsResource: Resource[JudgementProvider[Double]],
                                                         metricsCalculation: MetricsCalculation) extends Calculation[WeaklyTypedMap[String], Any] {
    def calculationResultIdentifier: Set[String] = metricsCalculation.metrics.map(x => x.name).toSet

    override val names: Set[String] = metricsCalculation.metrics.map(metric => metric.name).toSet

    override val calculation: SerializableFunction1[WeaklyTypedMap[String], Seq[ResultRecord[Any]]] = tMap => {
      val requestTemplate: RequestTemplate = tMap.get[RequestTemplate](REQUEST_TEMPLATE_STORAGE_KEY.name).get
      val query: String = requestTemplate.getParameter(queryParamName).map(x => x.head).getOrElse("")
      val productSequence: Seq[String] = tMap.get[Seq[String]](productIdsKey).getOrElse(Seq.empty)
      // TODO: abstract the node resource storage so this function can be moved
      // back the the base implementations and we just pass the right implementation
      // of resource storage
      val judgementsOrError: Either[RetrievalError[JudgementProvider[Double]], JudgementProvider[Double]] = ClusterNodeObj.getResource(RetrievalDirective.Retrieve(judgementsResource))
      judgementsOrError match {
        case Left(error) =>
          metricsCalculation.metrics.map(x => ResultRecord(x.name, Left(Seq(ComputeFailReason.apply(error.cause.toString)))))
        case Right(judgementProvider) =>
          metricsCalculation.calculateAllAndReturnSingleResults(query, productSequence, judgementProvider)
      }
    }
  }

}
