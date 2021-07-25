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


package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.processing.JobMessages.SearchEvaluation
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.RequestModifiers.RequestPermutation
import ConnectionJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import RequestPermutationJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors.RecursiveValueSelector
import de.awagen.kolibri.base.usecase.searchopt.io.json.JsonSelectorJsonProtocol._


object SearchEvaluationJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {

  implicit val queryAndParamProviderFormat: RootJsonFormat[SearchEvaluation] = jsonFormat(
    (
      jobName: String,
      fixedParams: Map[String, Seq[String]],
      contextPath: String,
      connections: Seq[Connection],
      requestPermutation: RequestPermutation,
      batchByIndex: Int,
      queryParam: String,
      productIdSelector: RecursiveValueSelector[String],
      excludeParamsFromMetricRow: Seq[String],
      judgementFileClasspathURI: String,
      tagByParam: String,
      writerDir: String,
      writerColumnSeparator: String,
      allowedTimePerElementInMillis: Int,
      allowedTimePerBatchInSeconds: Int,
      allowedTimeForJobInSeconds: Int,
      expectResultsFromBatchCalculations: Boolean
    ) =>
      SearchEvaluation.apply(
        jobName,
        fixedParams,
        contextPath,
        connections,
        requestPermutation,
        batchByIndex,
        queryParam,
        productIdSelector,
        excludeParamsFromMetricRow,
        judgementFileClasspathURI,
        tagByParam,
        writerDir,
        writerColumnSeparator,
        allowedTimePerElementInMillis,
        allowedTimePerBatchInSeconds,
        allowedTimeForJobInSeconds,
        expectResultsFromBatchCalculations
      ),
    "jobName",
    "fixedParams",
    "contextPath",
    "connections",
    "requestPermutation",
    "batchByIndex",
    "queryParam",
    "productIdSelector",
    "excludeParamsFromMetricRow",
    "judgementFileClasspathURI",
    "tagByParam",
    "writerDir",
    "writerColumnSeparator",
    "allowedTimePerElementInMillis",
    "allowedTimePerBatchInSeconds",
    "allowedTimeForJobInSeconds",
    "expectResultsFromBatchCalculations"
  )

}
