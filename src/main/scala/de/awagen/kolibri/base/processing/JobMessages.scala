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
import akka.util.Timeout
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.domain.jobdefinitions.TestJobDefinitions
import de.awagen.kolibri.base.processing.JobMessages.{SearchEvaluation, TestPiCalculation}
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.SearchJobDefinitions
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.values.AggregateValue

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.implicitConversions

object JobMessages {

  trait JobMessage extends KolibriSerializable

  case class TestPiCalculation(jobName: String, nrThrows: Int, batchSize: Int, resultDir: String) extends JobMessage

  case class SearchEvaluation(jobName: String,
                              parameters: OrderedMultiValues,
                              judgementsFileClassPathURI: String = "data/test_judgements.txt",
                              connections: Seq[Connection] = Seq(Connection(host = "localhost", port = 80, useHttps = false, credentialsProvider = None)),
                              resultFileURI: String = "/home/ed/REPOS/github/kolibri_release/kolibri-base/kolibri-test",
                              timeout: FiniteDuration = 60 minutes,
                              expectResultsFromBatchCalculations: Boolean = true) extends JobMessage
}


object JobMessagesImplicits {

  implicit class TestPiCalcToRunnable(calc: TestPiCalculation) {

    def toRunnable: SupervisorActor.ProcessActorRunnableJobCmd[Int, Double, Double, Map[Tag, AggregateValue[Double]]] = {
      TestJobDefinitions.piEstimationJob(
        jobName = calc.jobName,
        nrThrows = calc.nrThrows,
        batchSize = calc.batchSize,
        resultDir = calc.resultDir)
    }

  }

  implicit class SearchEvaluationToRunnable(eval: SearchEvaluation) {

    def toRunnable(implicit as: ActorSystem, ec: ExecutionContext, timeout: Timeout): SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] = {
      // TODO: this is just temporary stub picking predefined definition. Change to take the actual definition into account and create the actual ActorRunnableJobCmd
      // some fixed parameters (pick those from SearchEvaluation object)
      val fixedParams: Map[String, Seq[String]] = Map("k1" -> Seq("v1", "v2"), "k2" -> Seq("v3"))
      SearchJobDefinitions.processActorRunnableJobCmd(fixedParams)
    }
  }

}