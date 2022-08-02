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
import de.awagen.kolibri.base.directives.ResourceDirectives.ResourceDirective
import de.awagen.kolibri.base.domain.Connections.Connection
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.io.json.ConnectionJsonProtocol._
import de.awagen.kolibri.base.io.json.ExecutionJsonProtocol._
import de.awagen.kolibri.base.io.json.ResourceDirectiveJsonProtocol.GenericResourceDirectiveFormat
import de.awagen.kolibri.base.io.json.TaggingConfigurationsJsonProtocol._
import de.awagen.kolibri.base.processing.JobMessages.SearchEvaluationDefinition
import de.awagen.kolibri.base.processing.execution.functions.Execution
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ValueSeqGenDefinition
import de.awagen.kolibri.base.processing.tagging.TaggingConfigurations.BaseTaggingConfiguration
import de.awagen.kolibri.base.usecase.searchopt.io.json.CalculationsJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.io.json.ParsingConfigJsonProtocol
import de.awagen.kolibri.base.usecase.searchopt.io.json.ParsingConfigJsonProtocol._
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.{Calculation, CalculationResult, FutureCalculation}
import de.awagen.kolibri.base.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import de.awagen.kolibri.base.io.json.ParameterValuesJsonProtocol.ValueSeqGenDefinitionFormat


object SearchEvaluationJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport with WithStructDef {

  val JOB_NAME_FIELD = "jobName"
  val REQUEST_TASKS_FIELD = "requestTasks"
  val FIXED_PARAMS_FIELD = "fixedParams"
  val CONTEXT_PATH_FIELD = "contextPath"
  val CONNECTIONS_FIELD = "connections"
  val RESOURCE_DIRECTIVES_FIELD = "resourceDirectives"
  val REQUEST_PARAMETER_PERMUTATE_SEQ_FIELD = "requestParameterPermutateSeq"
  val BATCH_BY_INDEX_FIELD = "batchByIndex"
  val PARSING_CONFIG_FIELD = "parsingConfig"
  val EXCLUDE_PARAMS_FROM_METRIC_ROW_FIELD = "excludeParamsFromMetricRow"
  val REQUEST_TEMPLATE_STORAGE_KEY_FIELD = "requestTemplateStorageKey"
  val MAP_FUTURE_METRIC_ROW_CALCULATION_FIELD = "mapFutureMetricRowCalculation"
  val SINGLE_MAP_CALCULATIONS_FIELD = "singleMapCalculations"
  val TAGGING_CONFIGURATION_FIELD = "taggingConfiguration"
  val WRAP_UP_FUNCTION_FIELD = "wrapUpFunction"
  val ALLOWED_TIME_PER_ELEMENT_IN_MILLIS_FIELD = "allowedTimePerElementInMillis"
  val ALLOWED_TIME_PER_BATCH_IN_SECONDS_FIELD = "allowedTimePerBatchInSeconds"
  val ALLOWED_TIME_FOR_JOB_IN_SECONDS_FIELD = "allowedTimeForJobInSeconds"
  val EXPECT_RESULTS_FROM_BATCH_CALCULATIONS_FIELD = "expectResultsFromBatchCalculations"

  implicit val queryAndParamProviderFormat: RootJsonFormat[SearchEvaluationDefinition] = jsonFormat(
    (
      jobName: String,
      requestTasks: Int,
      fixedParams: Map[String, Seq[String]],
      contextPath: String,
      connections: Seq[Connection],
      resourceDirectives: Seq[ResourceDirective[_]],
      requestParameterPermutateSeq: Seq[ValueSeqGenDefinition[_]],
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
      SearchEvaluationDefinition.apply(
        jobName,
        requestTasks,
        fixedParams,
        contextPath,
        connections,
        resourceDirectives,
        requestParameterPermutateSeq,
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
    JOB_NAME_FIELD,
    REQUEST_TASKS_FIELD,
    FIXED_PARAMS_FIELD,
    CONTEXT_PATH_FIELD,
    CONNECTIONS_FIELD,
    RESOURCE_DIRECTIVES_FIELD,
    REQUEST_PARAMETER_PERMUTATE_SEQ_FIELD,
    BATCH_BY_INDEX_FIELD,
    PARSING_CONFIG_FIELD,
    EXCLUDE_PARAMS_FROM_METRIC_ROW_FIELD,
    REQUEST_TEMPLATE_STORAGE_KEY_FIELD,
    MAP_FUTURE_METRIC_ROW_CALCULATION_FIELD,
    SINGLE_MAP_CALCULATIONS_FIELD,
    TAGGING_CONFIGURATION_FIELD,
    WRAP_UP_FUNCTION_FIELD,
    ALLOWED_TIME_PER_ELEMENT_IN_MILLIS_FIELD,
    ALLOWED_TIME_PER_BATCH_IN_SECONDS_FIELD,
    ALLOWED_TIME_FOR_JOB_IN_SECONDS_FIELD,
    EXPECT_RESULTS_FROM_BATCH_CALCULATIONS_FIELD
  )

  override def structDef: JsonStructDefs.StructDef[_] = {
    NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(JOB_NAME_FIELD),
          RegexStructDef(".+".r),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(REQUEST_TASKS_FIELD),
          IntMinMaxStructDef(0, 1000),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(FIXED_PARAMS_FIELD),
          MapStructDef(StringStructDef, StringSeqStructDef),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(CONTEXT_PATH_FIELD),
          StringStructDef,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(CONNECTIONS_FIELD),
          GenericSeqStructDef(ConnectionJsonProtocol.structDef),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(RESOURCE_DIRECTIVES_FIELD),
          GenericSeqStructDef(GenericResourceDirectiveFormat.structDef),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(REQUEST_PARAMETER_PERMUTATE_SEQ_FIELD),
          GenericSeqStructDef(ParameterValuesJsonProtocol.ValueSeqGenDefinitionFormat.structDef),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(BATCH_BY_INDEX_FIELD),
          IntMinMaxStructDef(0, 1000),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(PARSING_CONFIG_FIELD),
          ParsingConfigJsonProtocol.structDef,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(EXCLUDE_PARAMS_FROM_METRIC_ROW_FIELD),
          StringSeqStructDef,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(REQUEST_TEMPLATE_STORAGE_KEY_FIELD),
          RegexStructDef(".+".r),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(MAP_FUTURE_METRIC_ROW_CALCULATION_FIELD),
          FromMapFutureCalculationSeqStringToMetricRowFormat.structDef,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(SINGLE_MAP_CALCULATIONS_FIELD),
          FromMapCalculationSeqBooleanToDoubleFormat.structDef,
          required = true
        ),
        FieldDef(
          StringConstantStructDef(TAGGING_CONFIGURATION_FIELD),
          TaggingConfigurationJsonFormat.structDef,
          required = false
        ),
        FieldDef(
          StringConstantStructDef(WRAP_UP_FUNCTION_FIELD),
          ExecutionJsonProtocol.ExecutionFormat.structDef,
          required = false
        ),
        FieldDef(
          StringConstantStructDef(ALLOWED_TIME_PER_ELEMENT_IN_MILLIS_FIELD),
          IntMinMaxStructDef(0, Int.MaxValue),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(ALLOWED_TIME_PER_BATCH_IN_SECONDS_FIELD),
          IntMinMaxStructDef(0, Int.MaxValue),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(ALLOWED_TIME_FOR_JOB_IN_SECONDS_FIELD),
          IntMinMaxStructDef(0, Int.MaxValue),
          required = true
        ),
        FieldDef(
          StringConstantStructDef(EXPECT_RESULTS_FROM_BATCH_CALCULATIONS_FIELD),
          BooleanStructDef,
          required = true
        )
      ),
      Seq.empty
    )
  }
}
