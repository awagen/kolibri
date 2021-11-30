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

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import de.awagen.kolibri.base.actors.clusterinfo.ClusterMetricsListenerActor.{MetricsProvided, ProvideMetrics}
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor._
import de.awagen.kolibri.base.actors.work.manager.JobManagerActor.WorkerStatusResponse
import de.awagen.kolibri.base.actors.work.manager.JobProcessingState.JobStatusInfo
import de.awagen.kolibri.base.actors.work.worker.RunnableExecutionActor.{BatchProcessState, BatchProcessStateResult}
import de.awagen.kolibri.base.config.AppProperties.config.{internalJobStatusRequestTimeout, kolibriDispatcherName}
import de.awagen.kolibri.base.http.server.routes.BaseRoutes.{clusterMetricsListenerActor, supervisorActor}
import de.awagen.kolibri.base.io.json.ClusterStatesJsonProtocol._
import de.awagen.kolibri.base.io.json.JobStateJsonProtocol.jobStatusFormat
import de.awagen.kolibri.base.processing.JobMessages.logger
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

object StatusRoutes extends CORSHandler {

  implicit val timeout: Timeout = Timeout(internalJobStatusRequestTimeout)

  import akka.http.scaladsl.server.Directives._
  import spray.json._
  import DefaultJsonProtocol._

  def batchStateToJson(state: Either[Throwable, BatchProcessState]): JsValue = {
    state match {
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
    val jsonSeq: Seq[JsValue] = response.result.map(x => {
      batchStateToJson(x.result)
    })
    jsonSeq.toJson.toString()
  }

  def getJobWorkerStatus(implicit system: ActorSystem): Route = {
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

  /**
    * Order BatchProcessStates by jobId and by batchNr (in that order)
    */
  val batchProcessStateOrdering: Ordering[BatchProcessState] = Ordering.by[BatchProcessState, String](_.jobId)
    .orElseBy(_.batchNr)

  def getAllJobWorkerStates(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("jobAllWorkerStates") {
        get {
          val jobIdsFuture: Future[Any] = supervisorActor ? ProvideAllRunningJobIDs
          val result: Future[Any] = jobIdsFuture.flatMap({
            case value: RunningJobs =>
              logger.debug(s"found running jobs: ${value.jobIDs}")
              val results: Seq[Future[Any]] = value.jobIDs.map(jobId => {
                (supervisorActor ? GetJobWorkerStatus(jobId))
                  // recover to make sure we have some value in case we run in timeout
                  .recover(err => WorkerStatusResponse(result = Seq(BatchProcessStateResult(jobId, -1, Left(err)))))
              })

              if (results.isEmpty) {
                Future.successful(Seq.empty[String].toJson.toString())
              }
              else {
                Future.sequence(results).map(values => {
                  values.asInstanceOf[Seq[WorkerStatusResponse]]
                    .flatMap(status => status.result)
                    .filter(x => x.result.isRight)
                    .map(x => x.result)
                    .sorted(Ordering[Either[Throwable, BatchProcessState]]({
                      case (Right(aa:BatchProcessState), Right(bb:BatchProcessState)) =>
                        batchProcessStateOrdering.compare(aa, bb)
                      case _ => 0
                    }))
                    .map(state => batchStateToJson(state))
                    .toJson.toString()
                })
                  .recover(e => Seq(workerStatusToJson(WorkerStatusResponse(Seq(BatchProcessStateResult("unknown", 0, Left(e)))))).toJson.toString())
              }
            case _ => Future.successful(Seq(workerStatusToJson(WorkerStatusResponse(Seq.empty))).toJson.toString())
          }).recover(e => {
            Seq(workerStatusToJson(WorkerStatusResponse(Seq(BatchProcessStateResult("unknown", 0, Left(e)))))).toJson.toString()
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

  def nodeState(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("nodeState") {
        get {
          onSuccess(clusterMetricsListenerActor ? ProvideMetrics) {
            case MetricsProvided(f) =>
              complete(f.toJson.toString())
            case _ =>
              complete(StatusCodes.ServerError.apply(500)("unexpected status response from server",
                "unexpected status response from server").toString())
          }
        }
      }
    )
  }

  def finishedJobStates(implicit system: ActorSystem): Route = {
    corsHandler(
      path("finishedJobStates") {
        get {
          onSuccess(supervisorActor ? ProvideJobHistory) {
            case result: JobHistory =>
              complete(StatusCodes.OK, s"""${result.jobs.toJson.toString()}""")
          }
        }
      })
  }

  def jobStates(implicit system: ActorSystem): Route = {
    implicit val ec: ExecutionContextExecutor = system.dispatchers.lookup(kolibriDispatcherName)
    corsHandler(
      path("jobStates") {
        get {
          onSuccess(supervisorActor ? ProvideAllRunningJobStates) {
            case result: Success[Seq[JobStatusInfo]] =>
              complete(StatusCodes.OK, s"""${result.get.toJson.toString()}""")
            case result: Failure[Any] =>
              complete(StatusCodes.ServerError.apply(500)(
                result.exception.getMessage,
                result.exception.getMessage).toString())
            case _ =>
              complete(StatusCodes.ServerError.apply(500)(
                "unexpected server response",
                "unexpected server response").toString())
          }
        }
      }
    )
  }

  def health(implicit system: ActorSystem): Route = {
    corsHandler(
      path("health") {
        get {
          complete(StatusCodes.OK)
        }
      })
  }

}
