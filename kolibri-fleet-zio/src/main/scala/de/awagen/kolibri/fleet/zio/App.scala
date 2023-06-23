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

import de.awagen.kolibri.fleet.zio.config.di.ZioDIConfig
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.TaskTopics
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{ClaimReader, JobStateReader, WorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.{ClaimWriter, FileStorageJobStateWriter, WorkStateWriter}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.{ClaimService, WorkHandlerService}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers
import zio._
import zio.http._
import zio.logging.LogFormat
import zio.logging.backend.SLF4J
import zio.stream.ZStream

object App extends ZIOAppDefault {

  override val bootstrap: ZLayer[Any, Nothing, Unit] = SLF4J.slf4j(LogLevel.Info, LogFormat.colored)

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
        TaskTopics.JobTaskProcessingTask)
      allProcessingClaimHashes <- ZIO.succeed(allProcessingClaims.map(x => x.nodeId))
      // manage claiming of job wrap up
      _ <- claimService.manageClaims(TaskTopics.JobWrapUpTask)
      // handle claimed task reset claims per node hash
      _ <- ZStream.fromIterable(allProcessingClaimHashes)
        .foreach(hash => claimService.manageClaims(TaskTopics.JobTaskResetTask(hash)))
      // manage claiming of new processing tasks
      _ <- claimService.manageClaims(TaskTopics.JobTaskProcessingTask)
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
      ZioDIConfig.taskOverviewServiceLayer >+>
      ZioDIConfig.claimServiceLayer >+>
      ZioDIConfig.workHandlerServiceLayer


  override val run: ZIO[Any, Throwable, Any] = {
    val fixed = Schedule.fixed(30 seconds)
    (for {
      _ <- ZIO.logInfo("Application started!")
      _ <- taskWorkerApp
      _ <- taskWorkerApp.repeat(fixed).delay(30 seconds).fork
      jobStateCache <- ServerEndpoints.openJobStateCache
      _ <- Server.serve(ServerEndpoints.jobPostingEndpoints ++ ServerEndpoints.statusEndpoints(jobStateCache))
      _ <- ZIO.logInfo("Application is about to exit!")
    } yield ())
      .provide(Server.default >+> combinedLayer)
  }
}
