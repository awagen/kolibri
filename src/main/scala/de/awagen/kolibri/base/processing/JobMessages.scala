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
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.domain.jobdefinitions.TestJobDefinitions
import de.awagen.kolibri.base.domain.jobdefinitions.TestJobDefinitions.MapWithCount
import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.base.processing.JobMessages.{SearchEvaluation, TestPiCalculation}
import de.awagen.kolibri.base.processing.execution.wrapup.JobWrapUpFunctions.JobWrapUpFunction
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.RequestPermutations.ModifierGeneratorProvider
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.base.processing.tagging.TaggingConfigurations.BaseTaggingConfiguration
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.SearchJobDefinitions
import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.{Calculation, CalculationResult, FutureCalculation}
import de.awagen.kolibri.base.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.values.AggregateValue

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

object JobMessages {

  trait JobMessage extends KolibriSerializable

  case class TestPiCalculation(jobName: String, nrThrows: Int, batchSize: Int, resultDir: String) extends JobMessage

  case class SearchEvaluation(jobName: String,
                              fixedParams: Map[String, Seq[String]],
                              contextPath: String,
                              connections: Seq[Connection],
                              requestPermutation: Seq[ModifierGeneratorProvider],
                              batchByIndex: Int,
                              queryParam: String,
                              parsingConfig: ParsingConfig,
                              excludeParamsFromMetricRow: Seq[String],
                              requestTemplateStorageKey: String,
                              mapFutureMetricRowCalculation: FutureCalculation[WeaklyTypedMap[String], MetricRow],
                              singleMapCalculations: Seq[Calculation[WeaklyTypedMap[String], CalculationResult[Double]]],
                              // TODO: change the second tagged element type to only the Either, and make it return tags which then at the appropriate places
                              // need to append the tags properly
                              taggingConfiguration: Option[BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]],
                              wrapUpFunction: Option[JobWrapUpFunction[Unit]],
                              allowedTimePerElementInMillis: Int = 1000,
                              allowedTimePerBatchInSeconds: Int = 600,
                              allowedTimeForJobInSeconds: Int = 7200,
                              expectResultsFromBatchCalculations: Boolean = true) extends JobMessage {
    val requestTemplateModifiers: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = requestPermutation.flatMap(x => x.modifiers)
  }
}


object JobMessagesImplicits {

  implicit class TestPiCalcToRunnable(calc: TestPiCalculation) {

    def toRunnable: SupervisorActor.ProcessActorRunnableJobCmd[Int, Double, Double, MapWithCount[Tag, AggregateValue[Double]]] = {
      TestJobDefinitions.piEstimationJob(
        jobName = calc.jobName,
        nrThrows = calc.nrThrows,
        batchSize = calc.batchSize,
        resultDir = calc.resultDir)
    }

  }

  implicit class SearchEvaluationToRunnable(eval: SearchEvaluation) {

    def toRunnable(implicit as: ActorSystem, ec: ExecutionContext): SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] = {
      SearchJobDefinitions.searchEvaluationToRunnableJobCmd(eval)
    }
  }

}