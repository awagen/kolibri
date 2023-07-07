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


package de.awagen.kolibri.definitions.processing

import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.MetricDocumentFormat
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.JsonTypeCast
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableConsumer, SerializableFunction1, SerializableSupplier}
import de.awagen.kolibri.datatypes.utils.MapUtils
import de.awagen.kolibri.datatypes.values.Calculations.Calculation
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType.AggregationType
import de.awagen.kolibri.definitions.directives.ResourceDirectives.{GenericResourceDirective, ResourceDirective}
import de.awagen.kolibri.definitions.directives.{ExpirePolicy, Resource, ResourceType, WithResources}
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.http.HttpMethod.HttpMethod
import de.awagen.kolibri.definitions.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.definitions.io.json.MetricFunctionJsonProtocol.{MetricFunction, MetricType}
import de.awagen.kolibri.definitions.processing.execution.functions.AggregationFunctions.AggregateFromDirectoryByRegexWeighted
import de.awagen.kolibri.definitions.processing.execution.functions.Execution
import de.awagen.kolibri.definitions.processing.modifiers.Modifier
import de.awagen.kolibri.definitions.processing.modifiers.ParameterValues.ValueSeqGenDefinition
import de.awagen.kolibri.definitions.processing.tagging.TaggingConfigurations
import de.awagen.kolibri.definitions.processing.tagging.TaggingConfigurations.{BaseTaggingConfiguration, EitherThrowableOrTaggedWeaklyTypedMapStore, TaggedMetricRowStore, TaggedRequestTemplateStore}
import de.awagen.kolibri.definitions.provider.WeightProviders
import de.awagen.kolibri.definitions.resources.ResourceProvider
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys.REQUEST_TEMPLATE_STORAGE_KEY
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.Calculations.JudgementsFromResourceIRMetricsCalculations
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.MetricsCalculation.AVAILABLE_JUDGEMENT_METRICS_NAME_TO_TYPE_MAPPING
import de.awagen.kolibri.definitions.usecase.searchopt.metrics.{IRMetricFunctions, JudgementHandlingStrategy, Metric, MetricsCalculation}
import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors.JsValueSeqSelector
import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors.{NamedAndTypedSelector, TypedJsonSeqSelector}
import de.awagen.kolibri.definitions.usecase.searchopt.parse.{JsonSelectors, ParsingConfig}
import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementProvider
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer

import scala.util.matching.Regex

object JobMessages {

  /**
   * Trait for objects containing job definitions. They are not meant to contain extensive state and
   * shall be fully serializable
   */
  trait JobDefinition extends KolibriSerializable {

    def jobName: String

    def requestTasks: Int

  }

  case class TestPiCalculationDefinition(jobName: String, requestTasks: Int, nrThrows: Int, batchSize: Int, resultDir: String) extends JobDefinition

  /**
   * This class contains only specification, and parameter combinations are loadable from
   * requestParameters. Data that needs to be pre-loaded on a node before actual execution
   * is given in resourceDirectives (per node data loading methods provided in ClusterNode)
   */
  case class SearchEvaluationDefinition(jobName: String,
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
                                        var metricNameToAggregationTypeMapping: Map[String, AggregationType],
                                        taggingConfiguration: Option[BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]],
                                        wrapUpFunction: Option[Execution[Any]],
                                        allowedTimePerElementInMillis: Int = 1000,
                                        allowedTimePerBatchInSeconds: Int = 600,
                                        allowedTimeForJobInSeconds: Int = 7200,
                                        expectResultsFromBatchCalculations: Boolean = true) extends JobDefinition with WithResources {

    import de.awagen.kolibri.definitions.processing.modifiers.ParameterValues.ParameterValuesImplicits._

    val excludeParamsFromMetricRow: Seq[String] = (fixedParams.keys.toSet ++ excludeParamColumns.toSet).toSeq

    metricNameToAggregationTypeMapping = metricNameToAggregationTypeMapping ++
      MetricsCalculation.AVAILABLE_JUDGEMENT_METRICS_NAME_TO_TYPE_MAPPING

    def requestTemplateModifiers: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] =
      requestParameters.map(x => x.toState).map(x => x.toSeqGenerator).map(x => x.mapGen(y => y.toModifier))

  }

  /**
   * This job config represents a reduced set of options to simplify the needed configuration input.
   * If full options are needed, refer to SearchEvaluationDefinition.
   * It assumes some common settings, e.g predefined sets of metrics and the like.
   *
   * @param jobName             - name of the job, also determining the name of the subfolder where results are stored.
   * @param connections         - connections to send the requests against (balanced)
   * @param fixedParams         - fixed parameters. They are by default excluded from the result file (e.g to avoid finer granularity of aggregations than wanted)
   * @param contextPath         - context path to be used for the composed URLs
   * @param judgementFilePath   - file path relative to the configured file system / bucket / basepath.
   * @param requestParameters   - the composition of actual parameters to compose the queries with. Define the permutations.
   * @param excludeParamColumns - further parameter names to exclude from the result file
   */
  case class QueryBasedSearchEvaluationDefinition(jobName: String,
                                                  connections: Seq[Connection],
                                                  fixedParams: Map[String, Seq[String]],
                                                  contextPath: String,
                                                  queryParameter: String,
                                                  httpMethod: HttpMethod,
                                                  productIdSelector: String,
                                                  resourceProvider: ResourceProvider,
                                                  fileToJudgementProviderFunc: SerializableFunction1[String, JudgementProvider[Double]],
                                                  wrapUpRegexToOverviewReaderFunc: SerializableFunction1[Regex, DataOverviewReader],
                                                  reader: Reader[String, Seq[String]],
                                                  writer: Writer[String, String, _],
                                                  metricDocumentFormatsMap: Map[String, MetricDocumentFormat],
                                                  otherSelectors: Seq[NamedAndTypedSelector[_]],
                                                  otherCalculations: Seq[Calculation[WeaklyTypedMap[String], Any]],
                                                  otherMetricNameToAggregationTypeMapping: Map[String, AggregationType],
                                                  judgementFilePath: String,
                                                  requestParameters: Seq[ValueSeqGenDefinition[_]],
                                                  excludeParamColumns: Seq[String],
                                                  outputResultsPath: String,
                                                  allowedTimePerElementInMillis: Int,
                                                  allowedTimePerBatchInSeconds: Int,
                                                  allowedTimeForJobInSeconds: Int
                                                 ) extends JobDefinition with WithResources {
    val kolibriJudgementsResourceKey = s"KOLIBRI_JUDGEMENTS-job=$jobName"
    val productIdsKey = "productIds"
    val requestTasks: Int = 4
    val resourceDirectives: Seq[ResourceDirective[_]] = Seq(
      GenericResourceDirective(
        new Resource[JudgementProvider[Double]](
          ResourceType.JUDGEMENT_PROVIDER,
          kolibriJudgementsResourceKey
        ),
        new SerializableSupplier[JudgementProvider[Double]] {
          override def apply(): JudgementProvider[Double] = fileToJudgementProviderFunc.apply(judgementFilePath)
        },
        ExpirePolicy.ON_JOB_END
      )
    )
    // assuming queries are the parameters at index 0
    val batchByIndex: Int = 0
    // config for parsing of certain elements from each response (we might need custom ones, too, though)
    val parsingConfig: ParsingConfig = ParsingConfig(
      Seq(
        TypedJsonSeqSelector(
          productIdsKey,
          JsonSelectors.JsonSelectorPathRegularExpressions.pathToSelector(productIdSelector)
            .map(x => x.asInstanceOf[JsValueSeqSelector]).get,
          JsonTypeCast.STRING
        )) ++ otherSelectors
    )
    val calculations: Seq[Calculation[WeaklyTypedMap[String], Any]] = Seq(
      JudgementsFromResourceIRMetricsCalculations(
        productIdsKey,
        REQUEST_TEMPLATE_STORAGE_KEY.name,
        queryParameter,
        new Resource[JudgementProvider[Double]](
          ResourceType.JUDGEMENT_PROVIDER,
          kolibriJudgementsResourceKey
        ),
        MetricsCalculation(
          Seq(
            Metric("NDCG@2", MetricFunction(MetricType.NDCG, 2, IRMetricFunctions.ndcgAtK(2))),
            Metric("NDCG@4", MetricFunction(MetricType.NDCG, 4, IRMetricFunctions.ndcgAtK(4))),
            Metric("NDCG@8", MetricFunction(MetricType.NDCG, 8, IRMetricFunctions.ndcgAtK(8))),
            Metric("NDCG@12", MetricFunction(MetricType.NDCG, 12, IRMetricFunctions.ndcgAtK(12))),
            Metric("NDCG@24", MetricFunction(MetricType.NDCG, 24, IRMetricFunctions.ndcgAtK(24))),
            Metric("PRECISION@k=2,t=0.2", MetricFunction(MetricType.PRECISION, 2, IRMetricFunctions.precisionAtK(2, 0.2))),
            Metric("PRECISION@k=4,t=0.2", MetricFunction(MetricType.PRECISION, 4, IRMetricFunctions.precisionAtK(4, 0.2))),
            Metric("RECALL@k=2,t=0.2", MetricFunction(MetricType.RECALL, 2, IRMetricFunctions.recallAtK(2, 0.2))),
            Metric("RECALL@k=4,t=0.2", MetricFunction(MetricType.RECALL, 2, IRMetricFunctions.recallAtK(4, 0.2)))
          ),
          JudgementHandlingStrategy.EXIST_RESULTS_AND_JUDGEMENTS_MISSING_AS_ZEROS
        ),
        resourceProvider
      )
    ) ++ otherCalculations
    val defaultAggregatorMappings: Map[String, AggregationType.Val[Double]] = Map(
      "NDCG@2" -> AggregationType.DOUBLE_AVG,
      "NDCG@4" -> AggregationType.DOUBLE_AVG,
      "NDCG@8" -> AggregationType.DOUBLE_AVG,
      "NDCG@12" -> AggregationType.DOUBLE_AVG,
      "NDCG@24" -> AggregationType.DOUBLE_AVG,
      "PRECISION@k=2,t=0.2" -> AggregationType.DOUBLE_AVG,
      "PRECISION@k=4,t=0.2" -> AggregationType.DOUBLE_AVG,
      "RECALL@k=2,t=0.2" -> AggregationType.DOUBLE_AVG,
      "RECALL@k=4,t=0.2" -> AggregationType.DOUBLE_AVG
    ) ++ AVAILABLE_JUDGEMENT_METRICS_NAME_TO_TYPE_MAPPING
    val metricNameToAggregationTypeMapping: Map[String, AggregationType] = MapUtils.combineMaps(defaultAggregatorMappings, otherMetricNameToAggregationTypeMapping, (x: AggregationType, y: AggregationType) => y)

    val filterFunc: SerializableFunction1[Tag, Boolean] = new SerializableFunction1[Tag, Boolean] {
      override def apply(v1: Tag): Boolean = true
    }
    val initTaggingConfig: SerializableConsumer[TaggedRequestTemplateStore] = TaggingConfigurations.requestByParameterTagger(queryParameter, AGGREGATION, filterFunc, extend = false)
    val processedTaggerConfig: SerializableConsumer[EitherThrowableOrTaggedWeaklyTypedMapStore] = new SerializableConsumer[EitherThrowableOrTaggedWeaklyTypedMapStore] {
      override def apply(v1: EitherThrowableOrTaggedWeaklyTypedMapStore): Unit = ()
    }
    val resultTaggerConfig: SerializableConsumer[TaggedMetricRowStore] = new SerializableConsumer[TaggedMetricRowStore] {
      override def apply(v1: TaggedMetricRowStore): Unit = ()
    }
    val taggingConfiguration: Option[BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]] = Some(
      BaseTaggingConfiguration(
        initTagger = initTaggingConfig,
        processedTagger = processedTaggerConfig,
        resultTagger = resultTaggerConfig
      )
    )
    val wrapUpFunction: Option[Execution[Any]] = Some(
      AggregateFromDirectoryByRegexWeighted(
        wrapUpRegexToOverviewReaderFunc,
        reader,
        writer,
        metricDocumentFormatsMap,
        s"$outputResultsPath/$jobName",
        s"$outputResultsPath/$jobName",
        s".*[(]$queryParameter=.+[)].*".r,
        WeightProviders.ConstantWeightProvider(1.0),
        "(ALL)"
      )
    )
    val expectResultsFromBatchCalculations: Boolean = false
  }
}
