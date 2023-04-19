package de.awagen.kolibri.fleet.akka.processing

import akka.actor.ActorSystem
import JobMessages.{QueryBasedSearchEvaluationDefinition, SearchEvaluationDefinition, TestPiCalculationDefinition}
import de.awagen.kolibri.base.processing.ProcessingMessages
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Aggregators.{fullJobToSingleTagAggregatorSupplier, singleBatchAggregatorSupplier}
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