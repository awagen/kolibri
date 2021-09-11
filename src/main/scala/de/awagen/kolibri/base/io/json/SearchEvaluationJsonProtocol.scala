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

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.io.json.ConnectionJsonProtocol._
import de.awagen.kolibri.base.io.json.ExecutionJsonProtocol._
import de.awagen.kolibri.base.io.json.ModifierGeneratorProviderJsonProtocol._
import de.awagen.kolibri.base.io.json.TaggingConfigurationsJsonProtocol._
import de.awagen.kolibri.base.processing.JobMessages.SearchEvaluation
import de.awagen.kolibri.base.processing.execution.functions.Execution
import de.awagen.kolibri.base.processing.modifiers.RequestPermutations.ModifierGeneratorProvider
import de.awagen.kolibri.base.processing.tagging.TaggingConfigurations.BaseTaggingConfiguration
import de.awagen.kolibri.base.usecase.searchopt.io.json.CalculationsJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.io.json.ParsingConfigJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.{Calculation, CalculationResult, FutureCalculation}
import de.awagen.kolibri.base.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


object SearchEvaluationJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {

  implicit val queryAndParamProviderFormat: RootJsonFormat[SearchEvaluation] = jsonFormat(
    (
      jobName: String,
      fixedParams: Map[String, Seq[String]],
      contextPath: String,
      connections: Seq[Connection],
      requestPermutation: Seq[ModifierGeneratorProvider],
      batchByIndex: Int,
      parsingConfig: ParsingConfig,
      excludeParamsFromMetricRow: Seq[String],
      requestTemplateStorageKey: String,
      mapFutureMetricRowCalculation: FutureCalculation[WeaklyTypedMap[String], Set[String], MetricRow],
      singleMapCalculations: Seq[Calculation[WeaklyTypedMap[String], CalculationResult[Double]]],
      taggingConfiguration: Option[BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]],
      wrapUpFunction: Option[Execution[Any]],
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
        parsingConfig,
        excludeParamsFromMetricRow,
        requestTemplateStorageKey,
        mapFutureMetricRowCalculation,
        singleMapCalculations,
        taggingConfiguration,
        wrapUpFunction,
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
    "parsingConfig",
    "excludeParamsFromMetricRow",
    "requestTemplateStorageKey",
    "mapFutureMetricRowCalculation",
    "singleMapCalculations",
    "taggingConfiguration",
    "wrapUpFunction",
    "allowedTimePerElementInMillis",
    "allowedTimePerBatchInSeconds",
    "allowedTimeForJobInSeconds",
    "expectResultsFromBatchCalculations"
  )

}
