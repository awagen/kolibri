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

import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.config.di.ZioDIConfig
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{ClaimReader, JobStateReader, WorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.{ClaimWriter, FileStorageJobStateWriter, WorkStateWriter}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.{TaskOverviewService, TaskPlannerService, WorkHandlerService}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers
import zio._
import zio.http._
import zio.logging.backend.SLF4J
import zio.stream.ZStream

object App extends ZIOAppDefault {

  override val bootstrap: ZLayer[Any, Any, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  /**
   * Effect taking care of claim and work management
   */
  val taskWorkerApp: ZIO[JobStateReader with WorkStateReader with TaskPlannerService with TaskOverviewService with WorkHandlerService, Throwable, Unit] = {
    for {
      jobStateReader <- ZIO.service[JobStateReader]
      workStateReader <- ZIO.service[WorkStateReader]
      taskOverviewService <- ZIO.service[TaskOverviewService]
      taskPlannerService <- ZIO.service[TaskPlannerService]
      workHandlerService <- ZIO.service[WorkHandlerService]
      openJobsState <- jobStateReader.fetchOpenJobState

      // getting next tasks to do from the distinct task topics
      jobToDoneTasks <- taskOverviewService.getJobToDoneTasks(openJobsState)
      batchProcessingTasks <- taskOverviewService.getBatchProcessingTasks(
        openJobsState,
        AppProperties.config.maxNrJobsClaimed
      )
      processingStates <- workStateReader.getInProgressStateForAllNodes(openJobsState.jobStateSnapshots.keySet)
        .map(x => x.values.flatMap(y => y.values).flatten.toSet)
      taskResetTasks <- ZStream.fromIterable(processingStates.map(x => x.processingInfo.processingNode))
        .flatMap(nodeHash => ZStream.fromIterableZIO(taskOverviewService.getTaskResetTasks(processingStates, nodeHash)))
        .runCollect

      _ <- ZIO.logInfo(s"APP: Open tasks for planning:")
      _ <- ZIO.logInfo(s"""- TASK RESET TASK:\n${taskResetTasks.mkString("\n")}""")
      _ <- ZIO.logInfo(s"""- TASK PROCESSING TASKS:\n${batchProcessingTasks.mkString("\n")}""")
      _ <- ZIO.logInfo(s"""- JOB TO DONE TASKS:\n${jobToDoneTasks.mkString("\n")}""")

      _ <- taskPlannerService.planTasks(taskResetTasks)
      _ <- taskPlannerService.planTasks(batchProcessingTasks)
      _ <- taskPlannerService.planTasks(jobToDoneTasks)

      // handle processing tasks
      _ <- workHandlerService.manageBatches(openJobsState)
      // persist the updated status of the batches processed
      _ <- workHandlerService.updateProcessStates
    } yield ()
  }

  val combinedLayer: ZLayer[Any, Nothing, Writers.Writer[String, String, _] with Reader[String, Seq[String]] with DataOverviewReader with ((String => Boolean) => DataOverviewReader) with JobStateReader with FileStorageJobStateWriter with ClaimReader with ClaimWriter with WorkStateReader with WorkStateWriter with TaskPlannerService with TaskOverviewService with WorkHandlerService] =
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
      ZioDIConfig.taskPlannerServiceLayer >+>
      ZioDIConfig.workHandlerServiceLayer


  override val run: ZIO[Any, Throwable, Any] = {
    val fixed = Schedule.fixed(30 seconds)
    (for {
      _ <- ZIO.logInfo("Application started!")
      _ <- taskWorkerApp.repeat(fixed).fork
      jobStateCache <- ServerEndpoints.openJobStateCache
      _ <- Server.serve(ServerEndpoints.jobPostingEndpoints ++ ServerEndpoints.statusEndpoints(jobStateCache))
      _ <- ZIO.logInfo("Application is about to exit!")
    } yield ())
      .provide(Server.default >+> combinedLayer)
  }
}
