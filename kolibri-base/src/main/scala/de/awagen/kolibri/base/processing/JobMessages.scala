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


package de.awagen.kolibri.base.processing

import akka.actor.ActorSystem
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.ProcessActorRunnableJobCmd
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages
import de.awagen.kolibri.base.config.AppConfig.persistenceModule
import de.awagen.kolibri.base.directives.ResourceDirectives.ResourceDirective
import de.awagen.kolibri.base.directives.WithResources
import de.awagen.kolibri.base.domain.Connections.Connection
import de.awagen.kolibri.base.domain.jobdefinitions.TestJobDefinitions
import de.awagen.kolibri.base.domain.jobdefinitions.TestJobDefinitions.MapWithCount
import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.JobMessages.{SearchEvaluationDefinition, TestPiCalculationDefinition}
import de.awagen.kolibri.base.processing.execution.functions.Execution
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ValueSeqGenDefinition
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.base.processing.tagging.TaggingConfigurations.BaseTaggingConfiguration
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.SearchJobDefinitions
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts.Aggregators.{fullJobToSingleTagAggregatorSupplier, singleBatchAggregatorSupplier}
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.Calculation
import de.awagen.kolibri.base.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.AggregateValue
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions
import scala.util.Random

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
   * requestParameterPermutateSeq. Data that needs to be pre-loaded on a node before actual execution
   * is given in resourceDirectives (per node data loading methods provided in ClusterNode)
   */
  case class SearchEvaluationDefinition(jobName: String,
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
                                        calculations: Seq[Calculation[WeaklyTypedMap[String], Double]],
                                        taggingConfiguration: Option[BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]],
                                        wrapUpFunction: Option[Execution[Any]],
                                        allowedTimePerElementInMillis: Int = 1000,
                                        allowedTimePerBatchInSeconds: Int = 600,
                                        allowedTimeForJobInSeconds: Int = 7200,
                                        expectResultsFromBatchCalculations: Boolean = true) extends JobDefinition with WithResources {

    import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ParameterValuesImplicits._

    def requestTemplateModifiers: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] =
      requestParameterPermutateSeq.map(x => x.toState).map(x => x.toSeqGenerator).map(x => x.mapGen(y => y.toModifier))

  }
}


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

  implicit class SearchEvaluationImplicits(eval: SearchEvaluationDefinition) extends RunnableConvertible {

    import scala.concurrent._

    def toRunnable(implicit as: ActorSystem, ec: ExecutionContext): SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] = {
      SearchJobDefinitions.searchEvaluationToRunnableJobCmd(eval)
    }

    def getWriter: Writer[MetricAggregation[Tag], Tag, Any] = {
      persistenceModule.persistenceDIModule.csvMetricAggregationWriter(subFolder = eval.jobName, x => {
        val randomAdd: String = Random.alphanumeric.take(5).mkString
        s"${x.toString()}-$randomAdd"
      })
    }

    def getBatchAggregationSupplier: () => Aggregators.Aggregator[ProcessingMessages.ProcessingMessage[MetricRow], MetricAggregation[Tag]] = singleBatchAggregatorSupplier

    def perJobAggregationSupplier: () => Aggregators.Aggregator[ProcessingMessages.ProcessingMessage[MetricRow], MetricAggregation[Tag]] = fullJobToSingleTagAggregatorSupplier
  }

}