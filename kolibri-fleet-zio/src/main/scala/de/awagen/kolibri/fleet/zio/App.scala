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
import de.awagen.kolibri.fleet.zio.config.di.ZioDIConfig
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.io.json.JobDefinitionJsonProtocol.JobDefinitionFormat
import de.awagen.kolibri.fleet.zio.resources.NodeResourceProvider
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopics
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{ClaimReader, JobStateReader, WorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.{ClaimWriter, FileStorageJobStateWriter, JobStateWriter, WorkStateWriter}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.{ClaimService, WorkHandlerService}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers
import spray.json.DefaultJsonProtocol.immSetFormat
import spray.json._
import zio._
import zio.http._
import zio.logging.LogFormat
import zio.logging.backend.SLF4J
import zio.stream.ZStream

object App extends ZIOAppDefault {

  override val bootstrap: ZLayer[Any, Nothing, Unit] = SLF4J.slf4j(LogLevel.Info, LogFormat.colored)

  private val app: Http[JobStateWriter with JobStateReader, Nothing, Request, Response] = Http.collectZIO[Request] {
    case Method.GET -> !! / "hello" => ZIO.succeed(Response.text("Hello World!"))
    case Method.GET -> !! / "global_resources" =>
      ZIO.attempt(Response.text(NodeResourceProvider.listResources.toJson.toString()))
        .catchAll(throwable =>
          ZIO.logError(s"error retrieving global resources:\n$throwable")) *> ZIO.succeed(Response.text("failed retrieving global resources"))
    case Method.GET -> !! / "registeredJobs" =>
      for {
        // TODO: avoid reloading the current state from filesystem on each request
        stateReader <- ZIO.service[JobStateReader]
        jobStateEither <- stateReader.fetchOpenJobState.either
        _ <- ZIO.when(jobStateEither.isLeft)(ZIO.logError(s"Retrieving job state failed with error:\n${jobStateEither.swap.toOption.get}"))
      } yield jobStateEither match {
        case Right(jobState) =>
          Response.text(s"Files: ${jobState.jobStateSnapshots.keys.toSeq.mkString(",")}")
        case Left(_) =>
          Response.text(s"Failed retrieving registered jobs")
      }
    case req@Method.POST -> !! / "job" =>
      (for {
        jobStateReader <- ZIO.service[JobStateReader]
        jobStateWriter <- ZIO.service[JobStateWriter]
        jobString <- req.body.asString
        jobDef <- ZIO.attempt(jobString.parseJson.convertTo[JobDefinition[_, _, _ <: WithCount]])
        // TODO: avoid reloading the current state from filesystem on each request
        jobState <- jobStateReader.fetchOpenJobState
        // TODO: here need to decompose existing job identifiers in job name and timestamp and use the job name for comparison
        jobFolderExists <- ZIO.attempt(jobState.jobStateSnapshots.contains(jobDef.jobName))
        _ <- ZIO.ifZIO(ZIO.succeed(jobFolderExists))(
          onFalse = jobStateWriter.storeJobDefinitionAndBatches(jobString),
          onTrue = ZIO.logInfo(s"Job folder for job ${jobDef.jobName} already exists," +
            s" skipping job information persistence step")
        )
        r <- ZIO.succeed(Response.text(jobString))
      } yield r).catchAll(throwable =>
        ZIO.logWarning(s"Error on posting job:\n$throwable")
          *> ZIO.succeed(Response.text(s"Failed posting job"))
      )
  }

  /**
   * Effect taking care of claim and work management
   */
  val taskWorkerApp: ZIO[JobStateReader with ClaimService with WorkHandlerService, Throwable, Unit] = {
    for {
      jobStateReaderService <- ZIO.service[JobStateReader]
      claimService <- ZIO.service[ClaimService]
      workHandlerService <- ZIO.service[WorkHandlerService]
      openJobsState <- jobStateReaderService.fetchOpenJobState
      allProcessingClaims <- claimService.getAllClaims(
        openJobsState.jobStateSnapshots.keySet,
        ClaimTopics.JobTaskProcessingClaim)
      allProcessingClaimHashes <- ZIO.succeed(allProcessingClaims.map(x => x.nodeId))
      // manage claiming of job wrap up
      _ <- claimService.manageClaims(ClaimTopics.JobWrapUpClaim)
      // handle claimed task reset claims per node hash
      _ <- ZStream.fromIterable(allProcessingClaimHashes)
        .foreach(hash => claimService.manageClaims(ClaimTopics.JobTaskResetClaim(hash)))
      // manage claiming of new processing tasks
      _ <- claimService.manageClaims(ClaimTopics.JobTaskProcessingClaim)
      // handle processing of claimed tasks
      _ <- workHandlerService.manageBatches(openJobsState)
      // persist the updated status of the batches processed
      _ <- workHandlerService.updateProcessStates
    } yield ()
  }

  val combinedLayer: ZLayer[Any, Nothing, Writers.Writer[String, String, _] with Reader[String, Seq[String]] with DataOverviewReader with ((String => Boolean) => DataOverviewReader) with JobStateReader with FileStorageJobStateWriter with ClaimReader with ClaimWriter with WorkStateReader with WorkStateWriter with ClaimService with WorkHandlerService] =
    ZioDIConfig.writerLayer >+>
      ZioDIConfig.readerLayer >+>
      ZioDIConfig.overviewReaderLayer >+>
      ZioDIConfig.fileFilterToOverViewFuncLayer >+>
      ZioDIConfig.jobStateReaderLayer >+>
      ZioDIConfig.jobStateWriterLayer >+>
      ZioDIConfig.claimReaderLayer >+>
      ZioDIConfig.claimWriterLayer >+>
      ZioDIConfig.workStateReaderLayer >+>
      ZioDIConfig.workStateWriterLayer >+>
      ZioDIConfig.claimServiceLayer >+>
      ZioDIConfig.workHandlerServiceLayer


  override val run: ZIO[Any, Throwable, Any] = {
    val fixed = Schedule.fixed(30 seconds)
    (for {
      _ <- ZIO.logInfo("Application started!")
      _ <- taskWorkerApp
      _ <- taskWorkerApp.repeat(fixed).delay(30 seconds).fork
      _ <- Server.serve(app)
      _ <- ZIO.logInfo("Application is about to exit!")
    } yield ())
      .provide(Server.default >+> combinedLayer)
  }
}
