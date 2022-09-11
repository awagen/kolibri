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

import de.awagen.kolibri.base.directives.{Resource, ResourceType}
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.JudgementsFromResourceIRMetricsCalculations

object CalculationsTestHelper {

  val CALCULATION_NAME = "testCalc"
  val QUERY_PARAM = "q"
  val REQUEST_TEMPLATE_KEY = "template"
  val PRODUCT_IDS_KEY = "pids"
  val NDCG2_NAME = "NDCG_2"
  val NDCG5_NAME = "NDCG_5"
  val NDCG10_NAME = "NDCG_10"

  def getJudgementBasedMetricsCalculation(judgementsResourceIdentifier: String,
                                          metrics: Seq[Metric]): JudgementsFromResourceIRMetricsCalculations = {
    JudgementsFromResourceIRMetricsCalculations(
      PRODUCT_IDS_KEY,
      QUERY_PARAM,
      Resource[Map[String, Double]](ResourceType.MAP_STRING_TO_DOUBLE_VALUE, judgementsResourceIdentifier),
      MetricsCalculation(
        metrics,
        JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS
      ))
  }

  def requestTemplateForQuery(query: String): RequestTemplate = {
    RequestTemplate("/", Map(QUERY_PARAM -> Seq(query)), Seq.empty)
  }

}
