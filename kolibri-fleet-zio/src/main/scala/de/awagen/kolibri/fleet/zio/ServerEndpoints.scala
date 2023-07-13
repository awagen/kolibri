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


package de.awagen.kolibri.fleet.zio

import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.definitions.io.json.ResourceJsonProtocol.AnyResourceFormat
import de.awagen.kolibri.fleet.zio.ServerEndpoints.ResponseContentProtocol.responseContentFormat
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.io.json.JobDefinitionJsonProtocol.JobDefinitionFormat
import de.awagen.kolibri.fleet.zio.io.json.JobDirectivesJsonProtocol.JobDirectivesFormat
import de.awagen.kolibri.fleet.zio.io.json.ProcessingStateJsonProtocol.processingStateFormat
import de.awagen.kolibri.fleet.zio.metrics.Metrics.CalculationsWithMetrics.countAPIRequests
import de.awagen.kolibri.fleet.zio.resources.NodeResourceProvider
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives.JobDirective
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{FileStorageJobStateReader, JobStateReader, NodeStateReader, WorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.JobStateWriter
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.{JobStateSnapshot, OpenJobsSnapshot}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.NodeUtilizationStates.NodeUtilizationStatesImplicits.nodeUtilizationStateFormat
import spray.json.DefaultJsonProtocol._
import spray.json._
import zio.cache.{Cache, Lookup}
import zio.http.Header.{AccessControlAllowMethods, AccessControlAllowOrigin}
import zio.http.HttpAppMiddleware.cors
import zio.http._
import zio.http.internal.middlewares.Cors.CorsConfig
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.stream.ZStream
import zio.{Task, ZIO, durationInt}

import java.nio.charset.Charset


object ServerEndpoints {

  val JOB_STATE_CACHE_KEY = "JOB_STATE"

  val corsConfig: CorsConfig = CorsConfig(
    allowedOrigin = _ => Some(AccessControlAllowOrigin.All),
    allowedMethods = AccessControlAllowMethods(Method.GET, Method.POST, Method.PUT, Method.DELETE)
  )

  private def jobStateRetrieval(jobStateReader: JobStateReader, isOpenJob: Boolean)(key: String): Task[OpenJobsSnapshot] = {
    key match {
      case JOB_STATE_CACHE_KEY => jobStateReader.fetchJobState(isOpenJob)
      case _ => ZIO.fail(new RuntimeException("unknown cache key for retrieving open job state"))
    }
  }

  def openJobStateCache: ZIO[JobStateReader, Nothing, Cache[String, Throwable, OpenJobsSnapshot]] = {
    for {
      reader <- ZIO.service[JobStateReader]
      cache <- Cache.make(
        capacity = 10,
        timeToLive = 10.seconds,
        lookup = Lookup(jobStateRetrieval(reader, isOpenJob = true))
      )
    } yield cache
  }

  def doneJobStateCache: ZIO[JobStateReader, Nothing, Cache[String, Throwable, OpenJobsSnapshot]] = {
    for {
      reader <- ZIO.service[JobStateReader]
      cache <- Cache.make(
        capacity = 50,
        timeToLive = 10.seconds,
        lookup = Lookup(jobStateRetrieval(reader, isOpenJob = false))
      )
    } yield cache
  }

  object JobStateSnapshotReduced {

    def fromJobStateSnapshot(jobStateSnapshot: JobStateSnapshot): JobStateSnapshotReduced = {
      JobStateSnapshotReduced(
        jobStateSnapshot.jobId,
        jobStateSnapshot.timePlacedInMillis,
        jobStateSnapshot.jobLevelDirectives,
        jobStateSnapshot.batchesToState
          .groupBy(x => x._2)
          .map(x => (x._1.toString, x._2.keySet.toSeq.count(_ => true)))
      )
    }

  }

  case class JobStateSnapshotReduced(jobId: String,
                                     timePlacedInMillis: Long,
                                     jobLevelDirectives: Set[JobDirective],
                                     batchCountPerState: Map[String, Int])

  implicit val jobStateSnapshotReducedFormat: RootJsonFormat[JobStateSnapshotReduced] = rootFormat(jsonFormat4(JobStateSnapshotReduced.apply))

  def directiveEndpoints = Http.collectZIO[Request] {
    case Method.DELETE -> !! / "jobs" / jobId / "directives" / "all" =>
      (for {
        jobStateWriter <- ZIO.service[JobStateWriter]
        _ <- jobStateWriter.removeAllDirectives(jobId)
        response <- ZIO.succeed(Response.json(ResponseContent(true, "").toJson.toString()))
      } yield response)
        .onError(cause => {
          ZIO.logError(s"error deleting all job level directives for job '$jobId':\nmsg: ${cause.failureOption.map(x => x.getMessage).getOrElse("")}\nCause: ${cause.trace.prettyPrint}")
        })
        .catchAll(_ => {
          ZIO.succeed(Response.json(ResponseContent("", s"failed to delete all job level directives for job '$jobId'").toJson.convertTo[String]).withStatus(Status.InternalServerError))
        }) @@ countAPIRequests("DELETE", "/jobs/[jobId]/directives/all")
    case req@Method.DELETE -> !! / "jobs" / jobId / "directives" =>
      (for {
        jobStateWriter <- ZIO.service[JobStateWriter]
        bodyStr <- req.body.asString(Charset.forName("UTF-8"))
        parsedDirectives <- ZIO.attempt(bodyStr.parseJson.convertTo[Seq[JobDirective]])
        _ <- jobStateWriter.removeDirectives(jobId, parsedDirectives.toSet)
        response <- ZIO.succeed(Response.json(ResponseContent(true, "").toJson.toString()))
      } yield response)
        .onError(cause => {
          ZIO.logError(s"error deleting passed set of job level directives for job '$jobId':\nmsg: ${cause.failureOption.map(x => x.getMessage).getOrElse("")}\nCause: ${cause.trace.prettyPrint}")
        })
        .catchAll(_ => {
          ZIO.succeed(Response.json(ResponseContent("", s"failed to delete passed set of job level directives for job '$jobId'").toJson.convertTo[String]).withStatus(Status.InternalServerError))
        }) @@ countAPIRequests("DELETE", "/jobs/[jobId]/directives")
    case req@Method.POST -> !! / "jobs" / jobId / "directives" =>
      (for {
        jobStateWriter <- ZIO.service[JobStateWriter]
        bodyStr <- req.body.asString(Charset.forName("UTF-8"))
        parsedDirectives <- ZIO.attempt(bodyStr.parseJson.convertTo[Seq[JobDirective]])
        _ <- jobStateWriter.writeDirectives(jobId, parsedDirectives.toSet)
        response <- ZIO.succeed(Response.json(ResponseContent(true, "").toJson.toString()))
      } yield response)
        .onError(cause => {
          ZIO.logError(s"error persisting passed set of job level directives for job '$jobId':\nmsg: ${cause.failureOption.map(x => x.getMessage).getOrElse("")}\nCause: ${cause.trace.prettyPrint}")
        })
        .catchAll(_ => {
          ZIO.succeed(Response.json(ResponseContent("", s"failed to persist passed set of job level directives for job '$jobId'").toJson.convertTo[String]).withStatus(Status.InternalServerError))
        }) @@ countAPIRequests("POST", "/jobs/[jobId]/directives")
  } @@ cors(corsConfig)

  def batchStatusEndpoints(jobStateCache: Cache[String, Throwable, OpenJobsSnapshot]) = Http.collectZIO[Request] {
    case Method.GET -> !! / "jobs" / "batches" =>
      (for {
        workStateReader <- ZIO.service[WorkStateReader]
        response <- jobStateCache.get(JOB_STATE_CACHE_KEY)
          .flatMap(state => workStateReader.getInProgressStateForAllNodes(state.jobStateSnapshots.keySet))
          .map(x => x.values.flatMap(y => y.values))
          .map(x => x.flatten)
          .map(x => Response.json(ResponseContent(x, "").toJson.toString()))
      } yield response
        )
        .onError(cause => {
          ZIO.logError(s"error retrieving batch states:\nmsg: ${cause.failureOption.map(x => x.getMessage).getOrElse("")}\nCause: ${cause.trace.prettyPrint}")
        })
        .catchAll(_ => {
          ZIO.succeed(Response.json(ResponseContent("", "failed retrieving batch states").toJson.convertTo[String]).withStatus(Status.InternalServerError))
        }) @@ countAPIRequests("GET", "/jobs/batches")
  } @@ cors(corsConfig)

  def statusEndpoints(openJobStateCache: Cache[String, Throwable, OpenJobsSnapshot],
                      doneJobStateCache: Cache[String, Throwable, OpenJobsSnapshot],
                      jobStateWriter: JobStateWriter) = (Http.collectZIO[Request] {
    case Method.GET -> !! / "health" => ZIO.succeed(Response.text("All good!"))
    case Method.GET -> !! / "resources" / "global" =>
      (for {
        resources <- ZIO.attempt(NodeResourceProvider.listResources)
        _ <- ZIO.logDebug(s"global resources: $resources")
        response <- ZIO.attempt(Response.json(ResponseContent(resources, "").toJson.toString()))
      } yield response)
        .onError(cause => {
          ZIO.logError(s"error retrieving global resources:\nmsg: ${cause.failureOption.map(x => x.getMessage).getOrElse("")}\n${cause.trace.prettyPrint}")
        })
        .catchAll(_ => ZIO.succeed(Response.json(ResponseContent("", "failed retrieving global resources").toJson.convertTo[String]))) @@ countAPIRequests("GET", "/resources/global")
    case Method.GET -> !! / "jobs" / "open" =>
      (for {
        jobStateEither <- openJobStateCache.get(JOB_STATE_CACHE_KEY).either
        result <- jobStateEither match {
          case Right(jobState) =>
            for {
              jobData <- ZIO.attempt({
                jobState.jobStateSnapshots.values.map(snapshot => {
                  JobStateSnapshotReduced.fromJobStateSnapshot(snapshot)
                })
              })
              _ <- ZIO.logDebug(s"Job data: $jobData")
              response <- ZIO.attempt(Response.json(ResponseContent(jobData, "").toJson.toString()))
                .onError(cause => {
                  ZIO.logError(s"Failed to write job snapshot state:\nmsg: ${cause.failureOption.map(x => x.getMessage)}, trace:\n${cause.trace.prettyPrint}")
                })
            } yield response
          case Left(_) =>
            for {
              _ <- ZIO.logError(s"Retrieving job state failed with error:\n${jobStateEither.swap.toOption.get}")
              response <- ZIO.attempt(Response.json(ResponseContent("", "Failed retrieving registered jobs").toJson.toString()).withStatus(Status.InternalServerError))
            } yield response

        }
      } yield result) @@ countAPIRequests("GET", "/jobs/open")
    case Method.GET -> !! / "jobs" / "done" =>
      (for {
        jobStateEither <- doneJobStateCache.get(JOB_STATE_CACHE_KEY).either
        result <- jobStateEither match {
          case Right(jobState) =>
            for {
              jobData <- ZIO.attempt({
                jobState.jobStateSnapshots.values.map(snapshot => {
                  JobStateSnapshotReduced.fromJobStateSnapshot(snapshot)
                })
              })
              _ <- ZIO.logDebug(s"Job data: $jobData")
              response <- ZIO.attempt(Response.json(ResponseContent(jobData, "").toJson.toString()))
                .onError(cause => {
                  ZIO.logError(s"Failed to write job snapshot state:\nmsg: ${cause.failureOption.map(x => x.getMessage)}, trace:\n${cause.trace.prettyPrint}")
                })
            } yield response
          case Left(_) =>
            for {
              _ <- ZIO.logError(s"Retrieving job state failed with error:\n${jobStateEither.swap.toOption.get}")
              response <- ZIO.attempt(Response.json(ResponseContent("", "Failed retrieving registered jobs").toJson.toString()).withStatus(Status.InternalServerError))
            } yield response
        }
      } yield result) @@ countAPIRequests("GET", "/jobs/done")
    // endpoint to delete a full job folder. Optional url-parameter isOpenJob (boolean)
    // to determine whether open or historical (done) job shall be deleted
    case req@Method.DELETE -> !! / "job" / jobId =>
      (for {
        isOpenJob <- ZIO.attempt(req.url.queryParams.getOrElse("isOpenJob", Seq("true")).head.toBoolean)
        _ <- jobStateWriter.removeJobFolder(jobId, isOpenJob)
        response <- ZIO.attempt(Response.json(ResponseContent(true, "").toJson.toString()))
      } yield response) @@ countAPIRequests("DELETE", "/job/[jobId]")
  } @@ cors(corsConfig))
    .catchAllZIO(throwable =>
      ZIO.logError(s"Error:\n${throwable.getStackTrace.mkString("\n")}") *>
        ZIO.succeed(Response.json(ResponseContent("", "failed loading data").toJson.toString()).withStatus(Status.InternalServerError)))


  case class ResponseContent[T](data: T, errorMessage: String)

  object ResponseContentProtocol extends DefaultJsonProtocol {
    implicit def responseContentFormat[T: JsonFormat]: RootJsonFormat[ResponseContent[T]] = jsonFormat2(ResponseContent.apply[T])
  }


  val jobPostingEndpoints = Http.collectZIO[Request] {
    case req@Method.POST -> !! / "job" =>
      (for {
        jobStateReader <- ZIO.service[JobStateReader]
        jobStateWriter <- ZIO.service[JobStateWriter]
        jobString <- req.body.asString
        jobDef <- ZIO.attempt(jobString.parseJson.convertTo[JobDefinition[_, _, _ <: WithCount]])
        jobState <- jobStateReader.fetchJobState(true)
        newJobSubFolder <- ZIO.attempt(s"${jobDef.jobName}_${java.lang.System.currentTimeMillis()}")
        // we extract the jobName and timePlacedInMillis info from the jobId to be able to compare solely on jobName
        existingJobNames <- ZStream.fromIterable(jobState.jobStateSnapshots.keySet)
          .mapZIO(jobId => ZIO.attempt(FileStorageJobStateReader.jobFolderNameToJobIdAndCreationTimeInMillis(jobId)))
          .either
          .filter({ case Left(_) => false; case Right(_) => true; })
          .map(x => x.toOption.get._1)
          .runCollect
        jobFolderExists <- ZIO.attempt(existingJobNames.contains(jobDef.jobName))
        response <- ZIO.ifZIO(ZIO.succeed(jobFolderExists))(
          onFalse = jobStateWriter.storeJobDefinitionAndBatches(jobString, newJobSubFolder)
            *> ZIO.succeed(Response.text(ResponseContent(jobString, "").toJson.toString())),
          onTrue = {
            val errorMsg = s"Job folder for job ${jobDef.jobName} already exists," +
              s" skipping job information persistence step"
            ZIO.logInfo(errorMsg) *> ZIO.succeed(
              Response.text(ResponseContent("", errorMsg).toJson.toString()).withStatus(Status.BadRequest)
            )
          }
        )
      } yield response).catchAll(throwable =>
        ZIO.logWarning(s"Error on posting job:\n$throwable")
          *> ZIO.succeed(Response.text(s"Failed posting job"))
      ) @@ countAPIRequests("POST", "/job")
  } @@ cors(corsConfig)

  val nodeStateEndpoint = Http.collectZIO[Request] {
    case Method.GET -> !! / "nodes" / "state" =>
      (for {
        stateReader <- ZIO.service[NodeStateReader]
        states <- stateReader.readNodeStates.catchAll(throwable => {
          ZIO.logError(s"Error trying to read node states:\n${throwable.getStackTrace.mkString("\n")}") *>
            ZIO.succeed(Seq.empty)
        })
      } yield Response.text(states.toJson.toString())) @@ countAPIRequests("GET", "/nodes/state")
  } @@ cors(corsConfig)

  val prometheusEndpoint = Http.collectZIO[Request] {
    case Method.GET -> !! / "metrics" =>
      ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
  } @@ cors(corsConfig)

}
