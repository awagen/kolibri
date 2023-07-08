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
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.io.json.JobDefinitionJsonProtocol.JobDefinitionFormat
import de.awagen.kolibri.fleet.zio.io.json.JobDirectivesJsonProtocol.JobDirectivesFormat
import de.awagen.kolibri.fleet.zio.io.json.ProcessingStateJsonProtocol.jsonFormat2
import de.awagen.kolibri.fleet.zio.resources.NodeResourceProvider
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives.JobDirective
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{FileStorageJobStateReader, JobStateReader, NodeStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.JobStateWriter
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.{JobStateSnapshot, OpenJobsSnapshot}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.NodeUtilizationStates.NodeUtilizationStatesImplicits.nodeUtilizationStateFormat
import spray.json.DefaultJsonProtocol.{IntJsonFormat, LongJsonFormat, StringJsonFormat, immSeqFormat, immSetFormat, iterableFormat, jsonFormat4, mapFormat, rootFormat}
import spray.json._
import zio.cache.{Cache, Lookup}
import zio.http.Header.{AccessControlAllowMethods, AccessControlAllowOrigin}
import zio.http.HttpAppMiddleware.cors
import zio.http._
import zio.http.internal.middlewares.Cors.CorsConfig
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.stream.ZStream
import zio.{Task, ZIO, durationInt}


object ServerEndpoints {

  val JOB_STATE_CACHE_KEY = "JOB_STATE"

  val corsConfig: CorsConfig = CorsConfig(
    allowedOrigin = _ => Some(AccessControlAllowOrigin.All),
    allowedMethods = AccessControlAllowMethods(Method.GET, Method.POST, Method.PUT, Method.DELETE)
  )

  private def jobStateRetrieval(jobStateReader: JobStateReader)(key: String): Task[OpenJobsSnapshot] = {
    key match {
      case JOB_STATE_CACHE_KEY => jobStateReader.fetchOpenJobState
      case _ => ZIO.fail(new RuntimeException("unknown cache key for retrieving open job state"))
    }
  }

  def openJobStateCache: ZIO[JobStateReader, Nothing, Cache[String, Throwable, OpenJobsSnapshot]] = {
    for {
      reader <- ZIO.service[JobStateReader]
      cache <- Cache.make(
        capacity = 10,
        timeToLive = 10.seconds,
        lookup = Lookup(jobStateRetrieval(reader))
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


  def statusEndpoints(jobStateCache: Cache[String, Throwable, OpenJobsSnapshot]) = (Http.collectZIO[Request] {
    case Method.GET -> !! / "health" => ZIO.succeed(Response.text("All good!"))
    case Method.GET -> !! / "resources" / "global" =>
      (for {
        resources <- ZIO.attempt(NodeResourceProvider.listResources)
        _ <- ZIO.logDebug(s"global resources: $resources")
        resourcesJson <- ZIO.attempt(resources.toJson.toString())
      } yield Response.text(resourcesJson))
        .onError(cause => {
          ZIO.logError(s"error retrieving global resources:\nmsg: ${cause.failureOption.map(x => x.getMessage).getOrElse("")}\n${cause.trace.prettyPrint}")
        })
        .catchAll(_ => ZIO.succeed(Response.text("failed retrieving global resources")))
    case Method.GET -> !! / "jobs" / "open" =>
      for {
        jobStateEither <- jobStateCache.get(JOB_STATE_CACHE_KEY).either
        result <- jobStateEither match {
          case Right(jobState) =>
            for {
              jobData <- ZIO.attempt({
                jobState.jobStateSnapshots.values.map(snapshot => {
                  JobStateSnapshotReduced.fromJobStateSnapshot(snapshot)
                })
              })
              _ <- ZIO.logDebug(s"Job data: $jobData")
              jobDataStr <- ZIO.attempt(jobData.toJson.toString())
                .onError(cause => {
                  ZIO.logError(s"Failed to write job snapshot state:\nmsg: ${cause.failureOption.map(x => x.getMessage)}, trace:\n${cause.trace.prettyPrint}")
                })
              responseContent <- ZIO.attempt(ResponseContent(jobDataStr, "").toJson.toString())
              _ <- ZIO.logDebug(s"found files: $jobData, content: $responseContent")
            } yield Response.text(responseContent)
          case Left(_) =>
            for {
              _ <- ZIO.logError(s"Retrieving job state failed with error:\n${jobStateEither.swap.toOption.get}")
              responseContent <- ZIO.attempt(ResponseContent("", "Failed retrieving registered jobs").toJson.toString())
            } yield Response.text(responseContent).withStatus(Status.InternalServerError)

        }
      } yield result
  } @@ cors(corsConfig))
    .catchAllZIO(throwable =>
      ZIO.logError(s"Error reading registered jobs:\n${throwable.getStackTrace.mkString("\n")}") *>
        ZIO.succeed(Response.text(ResponseContent("", "failed loading data").toJson.toString()).withStatus(Status.InternalServerError)))


  case class ResponseContent(data: String, errorMessage: String)

  implicit val responseContentFormat: RootJsonFormat[ResponseContent] = jsonFormat2(ResponseContent)


  val jobPostingEndpoints = Http.collectZIO[Request] {
    case req@Method.POST -> !! / "job" =>
      (for {
        jobStateReader <- ZIO.service[JobStateReader]
        jobStateWriter <- ZIO.service[JobStateWriter]
        jobString <- req.body.asString
        jobDef <- ZIO.attempt(jobString.parseJson.convertTo[JobDefinition[_, _, _ <: WithCount]])
        jobState <- jobStateReader.fetchOpenJobState
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
      )
  } @@ cors(corsConfig)

  val nodeStateEndpoint = Http.collectZIO[Request] {
    case Method.GET -> !! / "nodes" / "state" =>
      for {
        stateReader <- ZIO.service[NodeStateReader]
        states <- stateReader.readNodeStates.catchAll(throwable => {
          ZIO.logError(s"Error trying to read node states:\n${throwable.getStackTrace.mkString("\n")}") *>
            ZIO.succeed(Seq.empty)
        })
      } yield Response.text(states.toJson.toString())
  } @@ cors(corsConfig)

  val prometheusEndpoint = Http.collectZIO[Request] {
    case Method.GET -> !! / "metrics" =>
      ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
  } @@ cors(corsConfig)

}
