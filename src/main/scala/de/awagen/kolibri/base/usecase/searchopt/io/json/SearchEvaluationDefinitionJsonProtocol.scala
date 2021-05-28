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

//package de.awagen.kolibri.base.usecase.searchopt.io.json
//
//import de.awagen.kolibri.base.io.json.ConnectionJsonProtocol.connectionFormat
//import MetricsEvaluationJsonProtocol.metricsEvaluationFormat
//import de.awagen.kolibri.base.io.json.QueryAndParamProviderJsonProtocol.queryAndParamProviderFormat
//import de.awagen.kolibri.base.domain.Connection
//import de.awagen.kolibri.base.domain.jobdefinitions.provider.QueryAndParamProvider
//import de.awagen.kolibri.base.usecase.searchopt.domain.SearchEvaluationDefinition
//import de.awagen.kolibri.base.usecase.searchopt.metrics.MetricsEvaluation
//import spray.json.{DefaultJsonProtocol, RootJsonFormat}
//
//
//object SearchEvaluationDefinitionJsonProtocol extends DefaultJsonProtocol {
//
//  implicit val searchEvaluationDefinitionFormat: RootJsonFormat[SearchEvaluationDefinition] = jsonFormat(
//    (id: String, connections: Seq[Connection], queryAndParamProvider: QueryAndParamProvider, metricsEvaluation: MetricsEvaluation, queryParameter: String) => SearchEvaluationDefinition
//      .createSearchExperiment(id, connections, queryAndParamProvider, metricsEvaluation, queryParameter),
//    "id",
//    "connections",
//    "queryAndParamProvider",
//    "metricsEvaluation",
//    "queryParameter"
//  )
//}
