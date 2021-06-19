package de.awagen.kolibri.base.processing

import akka.actor.ActorSystem
import akka.util.Timeout
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.domain.Connection
import de.awagen.kolibri.base.domain.jobdefinitions.TestJobDefinitions
import de.awagen.kolibri.base.http.client.request.RequestTemplate
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
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions
import scala.concurrent.duration._

object JobMessages {

  trait JobMessage extends KolibriSerializable

  case class TestPiCalculation(jobName: String, nrThrows: Int, batchSize: Int, resultDir: String) extends JobMessage

  case class SearchEvaluation(parameters: OrderedMultiValues,
                              judgementsFileClassPathURI: String = "data/test_judgements.txt",
                              connections: Seq[Connection] = Seq(Connection(host = "localhost", port = 80, useHttps = false, credentialsProvider = None)),
                              resultFileURI: String = "/home/ed/REPOS/github/kolibri_release/kolibri-base/kolibri-test",
                              timeout: FiniteDuration = 60 minutes)
}


object JobMessagesImplicits {

  implicit class TestPiCalcToRunnable(calc: TestPiCalculation){

    def toRunnable: SupervisorActor.ProcessActorRunnableJobCmd[Int, Double, Double, Map[Tag, AggregateValue[Double]]] = {
      TestJobDefinitions.piEstimationJob(
        jobName = calc.jobName,
        nrThrows = calc.nrThrows,
        batchSize = calc.batchSize,
        resultDir = calc.resultDir)
    }

  }

  implicit class SearchEvaluationToRunnable(eval: SearchEvaluation) {

    def toRunnable(implicit as: ActorSystem, ec: ExecutionContext, timeout: Timeout): SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, (Either[Throwable, MetricRow], RequestTemplate), MetricRow, MetricAggregation[Tag]] = {
      SearchJobDefinitions.processActorRunnableJobCmd
    }
  }

//  val piCalc: TestPiCalculation = TestPiCalculation("job1", 3, 3, "")
//  piCalc.toRunnable

}