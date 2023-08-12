/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio.execution

import de.awagen.kolibri.datatypes.metrics.aggregation.mutable.MetricAggregation
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable._
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.mutable.Aggregators.TagKeyMetricAggregationPerClassAggregator
import de.awagen.kolibri.definitions.http.client.request.RequestTemplate
import de.awagen.kolibri.definitions.processing.JobMessages.{QueryBasedSearchEvaluationDefinition, SearchEvaluationDefinition}
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.definitions.processing.tagging.TaggingConfigurations.RequestAndParsingResultTaggerConfig
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.BatchGenerators.batchByGeneratorAtIndex
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.ReservedStorageKeys.REQUEST_TEMPLATE_STORAGE_KEY
import de.awagen.kolibri.fleet.zio.config.AppConfig
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.BatchAggregationInfo
import de.awagen.kolibri.fleet.zio.execution.TaskFactory._
import zio.ZIO
import zio.http.Client

import scala.util.Random

object JobMessagesImplicits {

  trait ZIOJobDefinitionConvertible {

    def toJobDef: ZIO[Client, Throwable, JobDefinitions.JobDefinition[_, _, _ <: WithCount]]

  }


  implicit class SearchEvaluationImplicits(eval: SearchEvaluationDefinition) extends ZIOJobDefinitionConvertible {

    private def getTaskSequenceForSearchEval: ZIO[Client, Throwable, Seq[ZIOTask[_]]] = {
      val random = new util.Random()
      // configuration for initial tagging based on the request template
      val requestTemplateTagger: SerializableConsumer[ProcessingMessage[RequestTemplate]] =
        eval.taggingConfiguration.map(x => x.initTagger).getOrElse(_ => ())
      val parsingResultTagger: SerializableConsumer[ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)]] =
        eval.taggingConfiguration.map(x => x.processedTagger).getOrElse(_ => ())

      for {
        httpClient <- ZIO.service[Client]
        requestTask <- ZIO.attempt(RequestJsonAndParseValuesTask(
          parsingConfig = eval.parsingConfig,
          taggingConfig = RequestAndParsingResultTaggerConfig(requestTemplateTagger, parsingResultTagger),
          // NOTE: while this randomly picks connections, it does not take care of
          // backpressure and the like, just using different connections for requests
          connectionSupplier = () => {
            val connectionIndex = random.between(0, eval.connections.length)
            eval.connections(connectionIndex)
          },
          contextPath = eval.contextPath,
          fixedParams = eval.fixedParams,
          httpMethod = eval.httpMethod.toString,
          successKeyName = "1-parsedValueMap",
          failKeyName = "1-parseFail",
          httpClient = httpClient
        ))
        metricCalculationTask <- ZIO.attempt(CalculateMetricsTask(
          requestAndParseSuccessKey = requestTask.successKey,
          requestTemplateKey = REQUEST_TEMPLATE_STORAGE_KEY.name,
          calculations = eval.calculations,
          metricNameToAggregationTypeMapping = eval.metricNameToAggregationTypeMapping,
          excludeParamsFromMetricRow = eval.excludeParamsFromMetricRow,
          successKeyName = "2-metricsRow",
          failKeyName = "2-metricsCalculationFail"
        ))
      } yield Seq(requestTask, metricCalculationTask)
    }

    private def httpClientToJobDef: ZIO[Client, Throwable, JobDefinitions.JobDefinition[RequestTemplateBuilderModifier, MetricRow, MetricAggregation[Tag]]] = {
      for {
        taskSequence <- getTaskSequenceForSearchEval
        metricRowResultKey <- ZIO.attempt(taskSequence.last.successKey)
        jobDef <- ZIO.attempt({
          JobDefinitions.JobDefinition(
            eval.jobName,
            eval.resourceDirectives,
            batchByGeneratorAtIndex(batchByIndex = eval.batchByIndex).apply(eval.requestTemplateModifiers),
            taskSequence,
            BatchAggregationInfo[MetricRow, MetricAggregation[Tag]](
              successKey = metricRowResultKey,
              batchAggregatorSupplier = () => new TagKeyMetricAggregationPerClassAggregator(
                keyMapFunction = identity,
                ignoreIdDiff = false
              ),
              writer = AppConfig.persistenceModule.persistenceDIModule.metricAggregationWriter(
                subFolder = eval.jobName,
                x => {
                  val randomAdd: String = Random.alphanumeric.take(5).mkString
                  s"${x.toString()}-$randomAdd"
                }
              )
            ),
            eval.wrapUpFunction.map(x => Seq(x)).getOrElse(Seq.empty)
          )
        })
      } yield jobDef



    }

    /**
     * NOTE: explicit types here in the job definition are important since the input type will be used as type
     * of the initial data point to start the computation. Thus if we write another type such as _ or Any,
     * the key value will not be found since the specifically typed key and the derived key do not match
     */
    override def toJobDef: ZIO[Client, Throwable, JobDefinitions.JobDefinition[RequestTemplateBuilderModifier, MetricRow, MetricAggregation[Tag]]] = {
      httpClientToJobDef
    }
  }

  implicit class QueryBasedSearchEvaluationImplicits(eval: QueryBasedSearchEvaluationDefinition) extends ZIOJobDefinitionConvertible {

    def toFullSearchEvaluation: SearchEvaluationDefinition = {
      SearchEvaluationDefinition(
        jobName = eval.jobName,
        requestTasks = eval.requestTasks,
        fixedParams = eval.fixedParams,
        contextPath = eval.contextPath,
        httpMethod = eval.httpMethod,
        connections = eval.connections,
        resourceDirectives = eval.resourceDirectives,
        requestParameters = eval.requestParameters,
        batchByIndex = eval.batchByIndex,
        parsingConfig = eval.parsingConfig,
        excludeParamColumns = eval.excludeParamColumns,
        calculations = eval.calculations,
        metricNameToAggregationTypeMapping = eval.metricNameToAggregationTypeMapping,
        taggingConfiguration = eval.taggingConfiguration,
        wrapUpFunction = eval.wrapUpFunction,
        allowedTimePerElementInMillis = eval.allowedTimePerElementInMillis,
        allowedTimePerBatchInSeconds = eval.allowedTimePerBatchInSeconds,
        allowedTimeForJobInSeconds = eval.allowedTimeForJobInSeconds,
        expectResultsFromBatchCalculations = eval.expectResultsFromBatchCalculations
      )
    }

    override def toJobDef: ZIO[Client, Throwable, JobDefinitions.JobDefinition[_, _, _ <: WithCount]] = {

      val searchEvalDef = eval.toFullSearchEvaluation
      searchEvalDef.toJobDef

    }
  }

}
