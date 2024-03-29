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


package de.awagen.kolibri.definitions.io.json

import de.awagen.kolibri.datatypes.io.json.EnumerationJsonProtocol.aggregateTypeFormat
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.MetricDocumentFormat
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import de.awagen.kolibri.datatypes.values.Calculations.Calculation
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType.AggregationType
import de.awagen.kolibri.definitions.io.json.EnumerationJsonProtocol.httpMethodFormat
import de.awagen.kolibri.definitions.directives.ResourceDirectives.ResourceDirective
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.http.HttpMethod
import de.awagen.kolibri.definitions.http.HttpMethod.HttpMethod
import de.awagen.kolibri.definitions.http.client.request.RequestTemplate
import de.awagen.kolibri.definitions.io.json.ConnectionJsonProtocol.connectionFormat
import de.awagen.kolibri.definitions.io.json.FIELD_KEYS._
import de.awagen.kolibri.definitions.io.json.ResourceDirectiveJsonProtocol.GenericResourceDirectiveFormatStruct
import de.awagen.kolibri.definitions.io.json.TaggingConfigurationsJsonProtocol.TaggingConfigurationJsonFormat
import de.awagen.kolibri.definitions.processing.JobMessages.{QueryBasedSearchEvaluationDefinition, SearchEvaluationDefinition}
import de.awagen.kolibri.definitions.processing.execution.functions.Execution
import de.awagen.kolibri.definitions.processing.modifiers.ParameterValues.ValueSeqGenDefinition
import de.awagen.kolibri.definitions.processing.tagging.TaggingConfigurations.BaseTaggingConfiguration
import de.awagen.kolibri.definitions.resources.ResourceProvider
import de.awagen.kolibri.definitions.usecase.searchopt.io.json.JsonSelectorJsonProtocol.NamedAndTypedSelectorFormat
import de.awagen.kolibri.definitions.usecase.searchopt.io.json.ParsingConfigJsonProtocol.parsingConfigJsonFormat
import de.awagen.kolibri.definitions.usecase.searchopt.io.json.{JsonSelectorJsonProtocol, ParsingConfigJsonProtocol}
import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors.JsonSelectorPathRegularExpressions.recursivePathKeyGroupingRegex
import de.awagen.kolibri.definitions.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors.NamedAndTypedSelector
import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementProvider
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, RootJsonFormat}

import scala.util.matching.Regex


object FIELD_KEYS {

  val JOB_NAME_FIELD = "jobName"
  val REQUEST_TASKS_FIELD = "requestTasks"
  val FIXED_PARAMS_FIELD = "fixedParams"
  val CONTEXT_PATH_FIELD = "contextPath"
  val CONNECTIONS_FIELD = "connections"
  val RESOURCE_DIRECTIVES_FIELD = "resourceDirectives"
  val REQUEST_PARAMETERS_FIELD = "requestParameters"
  val BATCH_BY_INDEX_FIELD = "batchByIndex"
  val PARSING_CONFIG_FIELD = "parsingConfig"
  val EXCLUDE_PARAMS_COLUMNS_FIELD = "excludeParamColumns"
  val CALCULATIONS_FIELD = "calculations"
  val METRIC_NAME_TO_AGGREGATION_TYPE_MAPPING_FIELD = "metricNameToAggregationTypeMapping"
  val TAGGING_CONFIGURATION_FIELD = "taggingConfiguration"
  val WRAP_UP_FUNCTION_FIELD = "wrapUpFunction"
  val ALLOWED_TIME_PER_ELEMENT_IN_MILLIS_FIELD = "allowedTimePerElementInMillis"
  val ALLOWED_TIME_PER_BATCH_IN_SECONDS_FIELD = "allowedTimePerBatchInSeconds"
  val ALLOWED_TIME_FOR_JOB_IN_SECONDS_FIELD = "allowedTimeForJobInSeconds"
  val EXPECT_RESULTS_FROM_BATCH_CALCULATIONS_FIELD = "expectResultsFromBatchCalculations"

  val QUERY_PARAMETER_FIELD = "queryParameter"
  val PRODUCT_ID_SELECTOR_FIELD = "productIdSelector"
  val OTHER_SELECTORS_FIELD = "otherSelectors"
  val OTHER_CALCULATIONS_FIELD = "otherCalculations"
  val OTHER_METRIC_NAME_TO_AGGREGATION_TYPE_MAPPING_FIELD = "otherMetricNameToAggregationTypeMapping"
  val JUDGEMENT_FILE_PATH_FIELD = "judgementFilePath"

  val HTTP_METHOD_FIELD = "httpMethod"

}

object SearchEvaluationJsonProtocol extends DefaultJsonProtocol {}


case class SearchEvaluationJsonProtocol(executionFormat: RootJsonFormat[Execution[Any]],
                                        resourceDirectiveJsonProtocol: ResourceDirectiveJsonProtocol,
                                        parameterValuesJsonProtocol: ParameterValuesJsonProtocol,
                                        calculationsJsonProtocol: CalculationsJsonProtocol,
                                        connectionFormatStruct: ConnectionFormatStruct) extends WithStructDef {
  import SearchEvaluationJsonProtocol._


  implicit object SearchEvaluationFormat extends RootJsonFormat[SearchEvaluationDefinition] {
    implicit val ef: RootJsonFormat[Execution[Any]] = executionFormat
    implicit val genericResourceDirectiveFormat: JsonFormat[ResourceDirective[_]] = resourceDirectiveJsonProtocol.GenericResourceDirectiveFormat
    implicit val valueSeqGenDefinitionFormat: JsonFormat[ValueSeqGenDefinition[_]] = parameterValuesJsonProtocol.ValueSeqGenDefinitionFormat
    implicit val calculationFormat: JsonFormat[Calculation[WeaklyTypedMap[String], Any]] = calculationsJsonProtocol.FromMapCalculationsFormat
    val format: RootJsonFormat[SearchEvaluationDefinition] = jsonFormat(
      (
        jobName: String,
        requestTasks: Int,
        fixedParams: Map[String, Seq[String]],
        contextPath: String,
        httpMethod: HttpMethod,
        connections: Seq[Connection],
        resourceDirectives: Seq[ResourceDirective[_]],
        requestParameters: Seq[ValueSeqGenDefinition[_]],
        batchByIndex: Int,
        parsingConfig: ParsingConfig,
        excludeParamColumns: Seq[String],
        calculations: Seq[Calculation[WeaklyTypedMap[String], Any]],
        metricNameToAggregationTypeMapping: Map[String, AggregationType],
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
          httpMethod,
          connections,
          resourceDirectives,
          requestParameters,
          batchByIndex,
          parsingConfig,
          excludeParamColumns,
          calculations,
          metricNameToAggregationTypeMapping,
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
      HTTP_METHOD_FIELD,
      CONNECTIONS_FIELD,
      RESOURCE_DIRECTIVES_FIELD,
      REQUEST_PARAMETERS_FIELD,
      BATCH_BY_INDEX_FIELD,
      PARSING_CONFIG_FIELD,
      EXCLUDE_PARAMS_COLUMNS_FIELD,
      CALCULATIONS_FIELD,
      METRIC_NAME_TO_AGGREGATION_TYPE_MAPPING_FIELD,
      TAGGING_CONFIGURATION_FIELD,
      WRAP_UP_FUNCTION_FIELD,
      ALLOWED_TIME_PER_ELEMENT_IN_MILLIS_FIELD,
      ALLOWED_TIME_PER_BATCH_IN_SECONDS_FIELD,
      ALLOWED_TIME_FOR_JOB_IN_SECONDS_FIELD,
      EXPECT_RESULTS_FROM_BATCH_CALCULATIONS_FIELD
    )

    override def write(obj: SearchEvaluationDefinition): JsValue = format.write(obj)

    override def read(json: JsValue): SearchEvaluationDefinition = format.read(json)
  }

  override def structDef: JsonStructDefs.StructDef[_] = {
    NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(JOB_NAME_FIELD),
          RegexStructDef(".+".r),
          required = true,
          description = "Name of the job, which is also used as folder name for the result output of the job."
        ),
        FieldDef(
          StringConstantStructDef(REQUEST_TASKS_FIELD),
          IntMinMaxStructDef(0, 1000),
          required = true,
          description = "Determines how many batches are executed at the same time. Note that the service settings of " +
            "max-connections and max-open-requests are important here. That means: each flow (batch execution) uses at most " +
            "max-connections requests, yet the number of batches running on a single node at the same time times max-connections shall not exceed " +
            "the max-open-requests setting on any single node. The batches are distributed evenly across nodes, " +
            "so we can roughly expect the max number of batches to run on a single node at a given time is " +
            "around (overall number of batches) / (number nodes)"
        ),
        FieldDef(
          StringConstantStructDef(FIXED_PARAMS_FIELD),
          MapStructDef(StringStructDef, StringSeqStructDef),
          required = true,
          description = "List of url parameters, where each parameter can have multiple values."
        ),
        FieldDef(
          StringConstantStructDef(CONTEXT_PATH_FIELD),
          StringStructDef,
          required = true,
          description = "The context path when composing the request."
        ),
        FieldDef(
          StringConstantStructDef(HTTP_METHOD_FIELD),
          StringChoiceStructDef(Seq(HttpMethod.GET.toString, HttpMethod.PUT.toString, HttpMethod.POST.toString)),
          required = true,
          description = "The http method to be used"
        ),
        FieldDef(
          StringConstantStructDef(CONNECTIONS_FIELD),
          GenericSeqStructDef(connectionFormatStruct.structDef),
          required = true,
          description = "The connections to utilize for sending requests. Can be very useful in cases such as when " +
            "multiple clusters of the requested system are available. In this case all of them can be requested and such " +
            "throughput significantly increased. The requesting of the external service happens in a balanced way, " +
            "e.g in case the different connections specified here have roughly the same latency, they should see roughly the " +
            "same amount of traffic."
        ),
        FieldDef(
          StringConstantStructDef(RESOURCE_DIRECTIVES_FIELD),
          GenericSeqStructDef(GenericResourceDirectiveFormatStruct.structDef),
          required = true,
          description = "Specifies which resources shall be loaded in the single node's global state (such as judgements for calculation of IR metrics). " +
            "The resources can be referenced in the single calculation definitions."
        ),
        FieldDef(
          StringConstantStructDef(REQUEST_PARAMETERS_FIELD),
          GenericSeqStructDef(ParameterValuesJsonProtocol.ValueSeqGenDefinitionFormatStruct.structDef),
          required = true,
          description = "Allows specification of combinations of url parameters, headers and bodies. " +
            "Note that standalone values are permutated with every other values, while mappings allow the mapping " +
            "of values of a key provider to other values that logically belong to that key. Can be used to restrict " +
            "the number of permutations to those that are actually meaningful."
        ),
        FieldDef(
          StringConstantStructDef(BATCH_BY_INDEX_FIELD),
          IntMinMaxStructDef(0, 1000),
          required = true,
          description = "Specifies by which settings from the requestParameters the batches are created. " +
            "The index refers to the specific order of parameters as specified in the requestParameters array. " +
            "This means that each batch has a single value of that specific parameter assigned, while containing " +
            "all further permutations. In case the index references a mapping, the batching happens " +
            "referring to the values serving as keys. It makes sense here to batch by the same parameter " +
            "on which the tagging is defined (if any), to avoid multiple files per tag."
        ),
        FieldDef(
          StringConstantStructDef(PARSING_CONFIG_FIELD),
          ParsingConfigJsonProtocol.structDef,
          required = true,
          description = "Allows the definition of values to be parsed from the responses. " +
            "Here '\\' means a plain selector, while '\\\\' means a recursive selection, e.g in case " +
            "of an array where each element contains a certain selector."
        ),
        FieldDef(
          StringConstantStructDef(EXCLUDE_PARAMS_COLUMNS_FIELD),
          StringSeqStructDef,
          required = true,
          description = "The results are aggregated based on non-metric fields in the result files. All fixed parameters " +
            "as specified in the fixedParams setting are excluded by default, yet parameters that are added " +
            "in the requestParameters setting will need to be excluded by entering the names of the parameters here, " +
            "otherwise the aggregation might be too granular."
        ),
        FieldDef(
          StringConstantStructDef(CALCULATIONS_FIELD),
          GenericSeqStructDef(
            calculationsJsonProtocol.FromMapCalculationsFormat.structDef
          ),
          required = true,
          description = "Calculations to be executed. Here the fields extracted in the parsingConfig are referenced. " +
            "Allows computation of common information retrieval (IR) metrics such as NDCG, ERR, Precision, Recall and " +
            "further metrics (such as distributional)."
        ),
        FieldDef(
          StringConstantStructDef(METRIC_NAME_TO_AGGREGATION_TYPE_MAPPING_FIELD),
          MapStructDef(StringStructDef, aggregateTypeFormat.structDef),
          required = true,
          description = "Mapping of metric names to aggregation type."
        ),
        FieldDef(
          StringConstantStructDef(TAGGING_CONFIGURATION_FIELD),
          TaggingConfigurationJsonFormat.structDef,
          required = false,
          description = "Defines the criteria for tagging. Tagging defines the granularity on which results are " +
            "grouped. A common selection would be to just tag by the input parameter that corresponds to the query. "
        ),
        FieldDef(
          StringConstantStructDef(WRAP_UP_FUNCTION_FIELD),
          ExecutionJsonProtocol.ExecutionFormat.structDef,
          required = false,
          description = "Wrap up function that is executed after the job finishes. Can be used to " +
            "aggregate all partial results into an overall result."
        ),
        FieldDef(
          StringConstantStructDef(ALLOWED_TIME_PER_ELEMENT_IN_MILLIS_FIELD),
          IntMinMaxStructDef(0, Int.MaxValue),
          required = true,
          description = "Defines how much a single element in a batch (e.g a single request) can take to " +
            "be fully processed (e.g parsed, evaluated)"
        ),
        FieldDef(
          StringConstantStructDef(ALLOWED_TIME_PER_BATCH_IN_SECONDS_FIELD),
          IntMinMaxStructDef(0, Int.MaxValue),
          required = true,
          description = "Defines how much time is allowed for a single batch to finish."
        ),
        FieldDef(
          StringConstantStructDef(ALLOWED_TIME_FOR_JOB_IN_SECONDS_FIELD),
          IntMinMaxStructDef(0, Int.MaxValue),
          required = true,
          description = "Defines how much time is allowed for the overall job to finish."
        ),
        FieldDef(
          StringConstantStructDef(EXPECT_RESULTS_FROM_BATCH_CALCULATIONS_FIELD),
          BooleanStructDef,
          required = true,
          description = "Only set to true if results shall be sent back to the central instance for aggregation. " +
            "The recommended way would be to leave this false such that per-tag results are written as results " +
            "to keep granularity and avoid serialization issues by sending around large single results " +
            "across the network."
        )
      ),
      Seq.empty
    )
  }

}

object QueryBasedSearchEvaluationJsonProtocol extends DefaultJsonProtocol

case class QueryBasedSearchEvaluationJsonProtocol(parameterValuesJsonProtocol: ParameterValuesJsonProtocol,
                                                  calculationsJsonProtocol: CalculationsJsonProtocol,
                                                  resourceProvider: ResourceProvider,
                                                  fileToJudgementProviderFunc: SerializableFunction1[String, JudgementProvider[Double]],
                                                  wrapUpRegexToOverviewReaderFunc: SerializableFunction1[Regex, DataOverviewReader],
                                                  reader: Reader[String, Seq[String]],
                                                  writer: Writer[String, String, _],
                                                  metricDocumentFormatsMap: Map[String, MetricDocumentFormat],
                                                  outputResultsPath: String,
                                                  allowedTimePerElementInMillis: Int,
                                                  allowedTimePerBatchInSeconds: Int,
                                                  allowedTimeForJobInSeconds: Int,
                                                  connectionFormatStruct: ConnectionFormatStruct
                                                 ) extends WithStructDef {

  import QueryBasedSearchEvaluationJsonProtocol._

  implicit object QueryBasedSearchEvaluationFormat extends RootJsonFormat[QueryBasedSearchEvaluationDefinition]  {
    implicit val valueSeqGenFormat: JsonFormat[ValueSeqGenDefinition[_]] = parameterValuesJsonProtocol.ValueSeqGenDefinitionFormat
    implicit val calculationFormat: JsonFormat[Calculation[WeaklyTypedMap[String], Any]] = calculationsJsonProtocol.FromMapCalculationsFormat
    val format: RootJsonFormat[QueryBasedSearchEvaluationDefinition] = jsonFormat(
      (
        jobName: String,
        connections: Seq[Connection],
        fixedParams: Map[String, Seq[String]],
        contextPath: String,
        queryParameter: String,
        httpMethod: HttpMethod,
        productIdSelector: String,
        otherSelectors: Seq[NamedAndTypedSelector[_]],
        otherCalculations: Seq[Calculation[WeaklyTypedMap[String], Any]],
        otherMetricNameToAggregationTypeMapping: Map[String, AggregationType],
        judgementFilePath: String,
        requestParameters: Seq[ValueSeqGenDefinition[_]],
        excludeParamColumns: Seq[String]
      ) =>
        QueryBasedSearchEvaluationDefinition.apply(
          jobName,
          connections,
          fixedParams,
          contextPath,
          queryParameter,
          httpMethod,
          productIdSelector,
          resourceProvider,
          fileToJudgementProviderFunc,
          wrapUpRegexToOverviewReaderFunc,
          reader,
          writer,
          metricDocumentFormatsMap,
          otherSelectors,
          otherCalculations,
          otherMetricNameToAggregationTypeMapping,
          judgementFilePath,
          requestParameters,
          excludeParamColumns,
          outputResultsPath,
          allowedTimePerElementInMillis,
          allowedTimePerBatchInSeconds,
          allowedTimeForJobInSeconds
        ),
      JOB_NAME_FIELD,
      CONNECTIONS_FIELD,
      FIXED_PARAMS_FIELD,
      CONTEXT_PATH_FIELD,
      QUERY_PARAMETER_FIELD,
      HTTP_METHOD_FIELD,
      PRODUCT_ID_SELECTOR_FIELD,
      OTHER_SELECTORS_FIELD,
      OTHER_CALCULATIONS_FIELD,
      OTHER_METRIC_NAME_TO_AGGREGATION_TYPE_MAPPING_FIELD,
      JUDGEMENT_FILE_PATH_FIELD,
      REQUEST_PARAMETERS_FIELD,
      EXCLUDE_PARAMS_COLUMNS_FIELD
    )

    override def write(obj: QueryBasedSearchEvaluationDefinition): JsValue = format.write(obj)

    override def read(json: JsValue): QueryBasedSearchEvaluationDefinition = format.read(json)
  }

  override def structDef: JsonStructDefs.StructDef[_] = {
    NestedFieldSeqStructDef(
      Seq(
        FieldDef(
          StringConstantStructDef(JOB_NAME_FIELD),
          RegexStructDef(".+".r),
          required = true,
          description = "Name of the job, which is also used as folder name for the result output of the job."
        ),
        FieldDef(
          StringConstantStructDef(CONNECTIONS_FIELD),
          GenericSeqStructDef(connectionFormatStruct.structDef),
          required = true,
          description = "The connections to utilize for sending requests. Can be very useful in cases such as when " +
            "multiple clusters of the requested system are available. In this case all of them can be requested and such " +
            "throughput significantly increased. The requesting of the external service happens in a balanced way, " +
            "e.g in case the different connections specified here have roughly the same latency, they should see roughly the " +
            "same amount of traffic."
        ),
        FieldDef(
          StringConstantStructDef(FIXED_PARAMS_FIELD),
          MapStructDef(StringStructDef, StringSeqStructDef),
          required = true,
          description = "List of url parameters, where each parameter can have multiple values."
        ),
        FieldDef(
          StringConstantStructDef(CONTEXT_PATH_FIELD),
          StringStructDef,
          required = true,
          description = "The context path when composing the request."
        ),
        FieldDef(
          StringConstantStructDef(QUERY_PARAMETER_FIELD),
          RegexStructDef(".+".r),
          required = true,
          description = "The url parameter holding the query value."
        ),
        FieldDef(
          StringConstantStructDef(HTTP_METHOD_FIELD),
          StringChoiceStructDef(Seq(HttpMethod.GET.toString, HttpMethod.PUT.toString, HttpMethod.POST.toString)),
          required = true,
          description = "The http method to be used"
        ),
        FieldDef(
          StringConstantStructDef(PRODUCT_ID_SELECTOR_FIELD),
          RegexStructDef(recursivePathKeyGroupingRegex),
          required = true,
          description = "The url parameter holding the query value."
        ),
        FieldDef(
          StringConstantStructDef(OTHER_SELECTORS_FIELD),
          GenericSeqStructDef(JsonSelectorJsonProtocol.NamedAndTypedSelectorFormat.structDef),
          required = true,
          description = "Allows specifying other fields to extract from the response that can be used" +
            "in custom calculations besides query-product based information retrieval metrics."
        ),
        FieldDef(
          StringConstantStructDef(OTHER_CALCULATIONS_FIELD),
          GenericSeqStructDef(
            calculationsJsonProtocol.FromMapCalculationsFormat.structDef
          ),
          required = true,
          description = "Calculations to be executed. Here the fields extracted in the parsingConfig are referenced. " +
            "Note that common information retrieval metrics are already covered in the default settings," +
            "thus calculations configured here allow either addition of further ones or specification of custom " +
            "metrics based on data extracted within the otherSelectors attribute."
        ),
        FieldDef(
          StringConstantStructDef(OTHER_METRIC_NAME_TO_AGGREGATION_TYPE_MAPPING_FIELD),
          MapStructDef(StringStructDef, aggregateTypeFormat.structDef),
          required = true,
          description = "Mapping of metric names to aggregation type."
        ),
        FieldDef(
          StringConstantStructDef(JUDGEMENT_FILE_PATH_FIELD),
          RegexStructDef(".+".r),
          required = true,
          description = "The path relative to the configured storage path to pick the judgement file from." +
            "Needs to be relative path specifying the full file name and suffix."
        ),
        FieldDef(
          StringConstantStructDef(REQUEST_PARAMETERS_FIELD),
          GenericSeqStructDef(ParameterValuesJsonProtocol.ValueSeqGenDefinitionFormatStruct.structDef),
          required = true,
          description = "Allows specification of combinations of url parameters, headers and bodies. " +
            "Note that standalone values are permutated with every other values, while mappings allow the mapping " +
            "of values of a key provider to other values that logically belong to that key. Can be used to restrict " +
            "the number of permutations to those that are actually meaningful."
        ),
        FieldDef(
          StringConstantStructDef(EXCLUDE_PARAMS_COLUMNS_FIELD),
          StringSeqStructDef,
          required = true,
          description = "The results are aggregated based on non-metric fields in the result files. All fixed parameters " +
            "as specified in the fixedParams setting are excluded by default, yet parameters that are added " +
            "in the requestParameters setting will need to be excluded by entering the names of the parameters here, " +
            "otherwise the aggregation might be too granular."
        )
      ),
      Seq.empty
    )
  }
}
