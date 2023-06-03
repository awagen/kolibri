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

import de.awagen.kolibri.datatypes.metrics.aggregation.immutable.MetricAggregation
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.ClassTyped
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators.TagKeyMetricAggregationPerClassAggregator
import de.awagen.kolibri.definitions.processing.JobMessages.{QueryBasedSearchEvaluationDefinition, SearchEvaluationDefinition}
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.BatchGenerators.batchByGeneratorAtIndex
import de.awagen.kolibri.fleet.zio.config.AppConfig
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.BatchAggregationInfo
import de.awagen.kolibri.fleet.zio.execution.TaskFactory._

import scala.util.Random

object JobMessagesImplicits {

  trait ZIOJobDefinitionConvertible {

    def toJobDef: JobDefinitions.JobDefinition[_, _, _ <: WithCount]

  }

  implicit class SearchEvaluationImplicits(eval: SearchEvaluationDefinition) extends ZIOJobDefinitionConvertible {

    def getTaskSequenceForSearchEval: Seq[ZIOTask[_]] = {

      val random = new util.Random()
      val requestTask = RequestJsonAndParseValuesTask(
        parsingConfig = eval.parsingConfig,
        // NOTE: while this randomly picks connections, it does not take care of
        // backpressure and the like, just using different connections for requests
        connectionSupplier = () => {
          val connectionIndex = random.between(0, eval.connections.length)
          eval.connections(connectionIndex)
        },
        contextPath = eval.contextPath,
        fixedParams = eval.fixedParams,
        successKeyName = "1-parsedValueMap",
        failKeyName = "1-parseFail"
      )
      val metricCalculationTask = CalculateMetricsTask(
        requestAndParseSuccessKey = requestTask.successKey,
        calculations = eval.calculations,
        metricNameToAggregationTypeMapping = eval.metricNameToAggregationTypeMapping,
        excludeParamsFromMetricRow = eval.excludeParamsFromMetricRow,
        successKeyName = "2-metricsRow",
        failKeyName = "2-metricsCalculationFail"
      )
      Seq(requestTask, metricCalculationTask)
    }

    override def toJobDef: JobDefinitions.JobDefinition[_, _, _ <: WithCount] = {
      val taskSequence = getTaskSequenceForSearchEval
      val metricRowResultKey = taskSequence.last.successKey.asInstanceOf[ClassTyped[ProcessingMessage[MetricRow]]]
      JobDefinitions.JobDefinition(
        eval.jobName,
        eval.resourceDirectives,
        batchByGeneratorAtIndex(batchByIndex = eval.batchByIndex).apply(eval.requestTemplateModifiers),
        getTaskSequenceForSearchEval,
        BatchAggregationInfo[MetricRow, MetricAggregation[Tag]](
          successKey = Right(metricRowResultKey),
          batchAggregatorSupplier = () => new TagKeyMetricAggregationPerClassAggregator(
            aggregationState = MetricAggregation.empty[Tag](identity),
            ignoreIdDiff = false
          ),
          writer = AppConfig.persistenceModule.persistenceDIModule.immutableMetricAggregationWriter(
            subFolder = eval.jobName,
            x => {
              val randomAdd: String = Random.alphanumeric.take(5).mkString
              s"${x.toString()}-$randomAdd"
            }
          )
        )
      )
    }
  }

  implicit class QueryBasedSearchEvaluationImplicits(eval: QueryBasedSearchEvaluationDefinition) extends ZIOJobDefinitionConvertible {

    def toFullSearchEvaluation: SearchEvaluationDefinition = {
      SearchEvaluationDefinition(
        jobName = eval.jobName,
        requestTasks = eval.requestTasks,
        fixedParams = eval.fixedParams,
        contextPath = eval.contextPath,
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

    override def toJobDef: JobDefinitions.JobDefinition[_, _, _ <: WithCount] = {

      val searchEvalDef = eval.toFullSearchEvaluation
      searchEvalDef.toJobDef

    }
  }

}
