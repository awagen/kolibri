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


package de.awagen.kolibri.base.http.server.routes

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Directives.{as, complete, delete, entity, get, onSuccess, parameters, path, post}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import de.awagen.kolibri.base.actors.clusterinfo.ClusterMetricsListenerActor
import de.awagen.kolibri.base.actors.clusterinfo.ClusterMetricsListenerActor.{MetricsProvided, ProvideMetrics}
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor._
import de.awagen.kolibri.base.actors.work.manager.JobManagerActor.WorkerStatusResponse
import de.awagen.kolibri.base.actors.work.worker.RunnableExecutionActor.BatchProcessStateResult
import de.awagen.kolibri.base.cluster.ClusterStates.ClusterStatus
import de.awagen.kolibri.base.config.AppProperties.config.{analyzeTimeout, internalJobStatusRequestTimeout, kolibriDispatcherName}
import de.awagen.kolibri.base.domain.jobdefinitions.TestJobDefinitions
import de.awagen.kolibri.base.http.server.routes.StatusRoutes.corsHandler
import de.awagen.kolibri.base.processing.JobMessages.{SearchEvaluation, TestPiCalculation, logger}
import de.awagen.kolibri.base.processing.execution.functions.Execution
import de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.SearchJobDefinitions
import spray.json.DefaultJsonProtocol.{JsValueFormat, StringJsonFormat, immSeqFormat}
import spray.json.{JsValue, enrichAny}

import java.util.Objects
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Random

/**
  * route examples: https://doc.akka.io/docs/akka-http/current/introduction.html#using-akka-http
  */
object BaseRoutes {

  private[routes] var supervisorActor: ActorRef = _
  private[routes] var clusterMetricsListenerActor: ActorRef = _

  def init(implicit system: ActorSystem): Unit = {
    this.synchronized {
      if (Objects.isNull(supervisorActor)) {
        supervisorActor = system.actorOf(SupervisorActor.props(true)
          .withDispatcher(kolibriDispatcherName))
      }
      if (Objects.isNull(clusterMetricsListenerActor)) {
        clusterMetricsListenerActor = system.actorOf(ClusterMetricsListenerActor.props)
      }
    }
  }

  def batchStateToJson(state: BatchProcessStateResult): JsValue = {
    import spray.json._
    import DefaultJsonProtocol._
    state.result match {
      case Right(value) =>
        Map("node" -> s"${value.node}",
          "jobId" -> s"${value.jobId}",
          "batchId" -> s"${value.batchNr}",
          "totalToProcess" -> s"${value.totalElements}",
          "totalProcessed" -> s"${value.processedElementCount}"
        ).toJson
      case Left(e) =>
        Map("exception" -> s"${e.getClass.getName}").toJson
    }
  }

  def workerStatusToJson(response: WorkerStatusResponse): String = {
    import spray.json._
    import DefaultJsonProtocol._
    val jsonSeq: Seq[JsValue] = response.result.map(x => {
      batchStateToJson(x)
    })
    jsonSeq.toJson.toString()
  }

  def extractHeaderValue(headerName: String): Seq[HttpHeader] => Option[String] = {
    x => {
      val value = x.filter(y => y.name() == headerName)
      if (value.nonEmpty) Some(value.head.value())
      else None
    }
  }

  lazy val simpleHelloRoute: Route = {
    corsHandler(
      path("hello") {
        get {
          complete(
            HttpEntity(
              ContentTypes.`text/html(UTF-8)`,
              "<h1>'Hi' by Kolibri</h1>"))
        }
      })
  }


  val numbers: Source[Int, NotUsed] = Source.fromIterator(() =>
    Iterator.continually(Random.nextInt())).take(100)

  // just an example of utilizing streaming response
  lazy val streamingUserRoutes: Route =
    corsHandler(
      path("random") {
        get {
          complete(
            HttpEntity(
              ContentTypes.`text/plain(UTF-8)`,
              // transform each number to a chunk of bytes
              numbers.map(n => {
                println(n)
                ByteString(s"$n\n")
              })
            )
          )
        }
      })


  def clusterStatusRoutee(implicit system: ActorSystem): Route = {
    val clusterStatusLoggingActor: ActorRef = system.actorOf(ClusterMetricsListenerActor.props, "RouteClusterMetricsActor")
    implicit val timeout: Timeout = 10.seconds

    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import akka.http.scaladsl.server.Directives._
    import de.awagen.kolibri.base.cluster.ClusterStates.ClusterStatusImplicits._
    import spray.json.DefaultJsonProtocol._

    corsHandler(
      path("clusterstatus") {
        get {
          onSuccess(clusterStatusLoggingActor ? ProvideMetrics) {
            case MetricsProvided(stats: Seq[ClusterStatus]) => complete(stats)
            case _ => complete(StatusCodes.NotFound)
          }
        }
      })
  }

  def killAllJobs(implicit system: ActorSystem): Route = {
    corsHandler(
      path("killall") {
        delete {
          supervisorActor ! KillAllChildren
          complete(StatusCodes.OK)
        }
      })
  }

  // the division of pi * r^2 / (2*r)^2 for throwing dart in unit-square with circle of radius 0.5
  // for unit-sware is pi / 4 -> calculate freq of darts within circle, multiply by 4, gives pi
  // estimation will be more accurate for more darts
  def executeDistributedPiCalculationExample(implicit system: ActorSystem): Route = {

    implicit val timeout: Timeout = Timeout(1 minute)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("pi_calc") {
        post {
          parameters("jobName", "nrThrows", "batchSize", "resultDir") { (jobName, nrThrows, batchSize, resultDir) => {
            val actorRunnableJob = TestJobDefinitions.piEstimationJob(jobName, nrThrows.toInt, batchSize.toInt, resultDir)
            supervisorActor ! actorRunnableJob
            complete(StatusCodes.Accepted, "Processing Pi Calculation Example")
          }
          }
        }
      })
  }

  def executeDistributedPiCalculationExampleWithoutSerialization(implicit system: ActorSystem): Route = {

    implicit val timeout: Timeout = Timeout(1 minute)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("pi_calc_no_ser") {
        post {
          parameters("jobName", "requestTasks", "nrThrows", "batchSize", "resultDir") { (jobName, requestTasks, nrThrows, batchSize, resultDir) => {
            val msg = TestPiCalculation(jobName, requestTasks.toInt, nrThrows.toInt, batchSize.toInt, resultDir)
            supervisorActor ! msg
            complete(StatusCodes.Accepted, "Processing Pi Calculation (without full ActorRunnable serialization) Example")
          }
          }
        }
      })
  }

  def getJobWorkerStatus(implicit system: ActorSystem): Route = {

    implicit val timeout: Timeout = Timeout(1 minute)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("jobWorkerStatus") {
        get {
          parameters("jobId") { jobId => {
            onSuccess(supervisorActor ? GetJobWorkerStatus(jobId)) {
              e => complete(workerStatusToJson(e.asInstanceOf[WorkerStatusResponse]))
            }
          }
          }
        }
      }
    )
  }

  def getAllJobWorkerStates(implicit system: ActorSystem): Route = {
    implicit val timeout: Timeout = Timeout(1 minute)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("jobAllWorkerStates") {
        get {
          val jobIdsFuture: Future[Any] = supervisorActor ? ProvideAllRunningJobIDs
          val result: Future[Any] = jobIdsFuture.flatMap({
            case value: RunningJobs =>
              logger.debug(s"found running jobs: ${value.jobIDs}")
              val results: Seq[Future[Any]] = value.jobIDs.map(jobId => supervisorActor ? GetJobWorkerStatus(jobId))
              if (results.isEmpty) {
                Future.successful(Seq.empty[String].toJson.toString())
              }
              else {
                Future.sequence(results).map(values => {
                  values.asInstanceOf[Seq[WorkerStatusResponse]]
                    .flatMap(status => status.result)
                    .map(state => batchStateToJson(state))
                    .toJson.toString()
                })
                  .recover(e => Seq(workerStatusToJson(WorkerStatusResponse(Seq(BatchProcessStateResult(Left(e)))))).toJson.toString())
              }
            case _ => Future.successful(Seq(workerStatusToJson(WorkerStatusResponse(Seq.empty))).toJson.toString())
          }).recover(e => {
            Seq(workerStatusToJson(WorkerStatusResponse(Seq(BatchProcessStateResult(Left(e)))))).toJson.toString()
          })
          onSuccess(result) {
            e =>
              logger.debug(s"result: $e")
              complete(e.toString)
          }
        }
      })
  }

  def getRunningJobIds(implicit system: ActorSystem): Route = {
    implicit val timeout: Timeout = Timeout(internalJobStatusRequestTimeout)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("getRunningJobIDs") {
        get {
          onSuccess(supervisorActor ? ProvideAllRunningJobIDs) {
            e => complete(e.toString)
          }
        }
      })
  }


  def getJobStatus(implicit system: ActorSystem): Route = {
    implicit val timeout: Timeout = Timeout(internalJobStatusRequestTimeout)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("getJobStatus") {
        get {
          parameters("jobId") {
            jobId => {
              onSuccess(supervisorActor ? ProvideJobState(jobId)) {
                e => complete(e.toString)
              }
            }
          }
        }
      })
  }

  def killJob(implicit system: ActorSystem): Route = {

    implicit val timeout: Timeout = Timeout(internalJobStatusRequestTimeout)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("stopJob") {
        delete {
          parameters("jobId") {
            jobId => {
              onSuccess(supervisorActor ? StopJob(jobId)) {
                e => complete(e.toString)
              }
            }
          }
        }
      })
  }

  def startSearchEval(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    import de.awagen.kolibri.base.io.json.SearchEvaluationJsonProtocol._
    corsHandler(
      path("search_eval") {
        post {
          entity(as[SearchEvaluation]) {
            searchEvaluation =>
              supervisorActor ! SearchJobDefinitions.searchEvaluationToRunnableJobCmd(searchEvaluation)
              complete(StatusCodes.Accepted, "Starting search evaluation example")
          }
        }
      })
  }

  def startSearchEvalNoSerialize(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)

    import de.awagen.kolibri.base.io.json.SearchEvaluationJsonProtocol._

    corsHandler(
      path("search_eval_no_ser") {
        post {
          entity(as[SearchEvaluation]) {
            searchEvaluation =>
              supervisorActor ! searchEvaluation
              complete(StatusCodes.Accepted, "Starting search evaluation example")
          }
        }
      })
  }

  def startExecution(implicit system: ActorSystem): Route = {
    implicit val timeout: Timeout = Timeout(analyzeTimeout)
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)

    import de.awagen.kolibri.base.io.json.ExecutionJsonProtocol._

    corsHandler(
      path("execution") {
        post {
          entity(as[Execution[Any]]) {
            execution =>
              onSuccess(supervisorActor ? execution) {
                e => complete(e.toString)
              }
          }
        }
      })
  }
}
