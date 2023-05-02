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


package de.awagen.kolibri.fleet.akka.processing

import akka.actor.ActorSystem
import de.awagen.kolibri.definitions.processing.JobMessages.{QueryBasedSearchEvaluationDefinition, SearchEvaluationDefinition, TestPiCalculationDefinition}
import de.awagen.kolibri.definitions.processing.ProcessingMessages
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.definitions.usecase.searchopt.jobdefinitions.parts.Aggregators.{fullJobToSingleTagAggregatorSupplier, singleBatchAggregatorSupplier}
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.{AggregateValue, Aggregators}
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.fleet.akka.actors.work.aboveall.SupervisorActor.ProcessActorRunnableJobCmd
import de.awagen.kolibri.fleet.akka.config.AppConfig.persistenceModule
import de.awagen.kolibri.fleet.akka.jobdefinitions.TestJobDefinitions
import de.awagen.kolibri.fleet.akka.jobdefinitions.TestJobDefinitions.MapWithCount
import de.awagen.kolibri.fleet.akka.usecase.searchopt.jobdefinitions.SearchJobDefinitions
import de.awagen.kolibri.storage.io.writer.Writers.Writer

import scala.concurrent.ExecutionContext
import scala.util.Random

object JobMessagesImplicits {

  trait RunnableConvertible {

    def toRunnable(implicit as: ActorSystem, ec: ExecutionContext): ProcessActorRunnableJobCmd[_, _, _, _ <: WithCount]

  }

  implicit class TestPiCalcToRunnable(calc: TestPiCalculationDefinition) extends RunnableConvertible {

    def toRunnable(implicit as: ActorSystem, ec: ExecutionContext): SupervisorActor.ProcessActorRunnableJobCmd[Int, Double, Double, MapWithCount[Tag, AggregateValue[Double]]] = {
      TestJobDefinitions.piEstimationJob(
        jobName = calc.jobName,
        nrThrows = calc.nrThrows,
        batchSize = calc.batchSize,
        resultDir = calc.resultDir)
    }
  }

  implicit class QueryBasedSearchEvaluationImplicits(eval: QueryBasedSearchEvaluationDefinition) extends RunnableConvertible {
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

    override def toRunnable(implicit as: ActorSystem, ec: ExecutionContext): ProcessActorRunnableJobCmd[_, _, _, _ <: WithCount] = {
      val searchEval = toFullSearchEvaluation
      searchEval.toRunnable
    }
  }

  implicit class SearchEvaluationImplicits(eval: SearchEvaluationDefinition) extends RunnableConvertible {

    import scala.concurrent._

    def toRunnable(implicit as: ActorSystem, ec: ExecutionContext): SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] = {
      SearchJobDefinitions.searchEvaluationToRunnableJobCmd(eval)
    }

    def getWriter: Writer[MetricAggregation[Tag], Tag, Any] = {
      persistenceModule.persistenceDIModule.metricAggregationWriter(
        subFolder = eval.jobName,
        x => {
          val randomAdd: String = Random.alphanumeric.take(5).mkString
          s"${x.toString()}-$randomAdd"
        }
      )
    }

    def getBatchAggregationSupplier: () => Aggregators.Aggregator[ProcessingMessages.ProcessingMessage[MetricRow], MetricAggregation[Tag]] = singleBatchAggregatorSupplier

    def perJobAggregationSupplier: () => Aggregators.Aggregator[ProcessingMessages.ProcessingMessage[MetricRow], MetricAggregation[Tag]] = fullJobToSingleTagAggregatorSupplier
  }

}
