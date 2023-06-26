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
import de.awagen.kolibri.fleet.zio.config.AppProperties.config.http_server_port
import de.awagen.kolibri.fleet.zio.config.di.ZioDIConfig
import de.awagen.kolibri.fleet.zio.metrics.Metrics.MetricTypes.taskManageCycleInvokeCount
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{JobStateReader, WorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.{TaskOverviewService, TaskPlannerService, WorkHandlerService}
import zio._
import zio.http._
import zio.logging.backend.SLF4J
import zio.metrics.connectors.{MetricsConfig, prometheus}
import zio.metrics.jvm.DefaultJvmMetrics
import zio.stream.ZStream

object App extends ZIOAppDefault {

  override val bootstrap: ZLayer[Any, Any, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val planTasksEffect: ZIO[JobStateReader with TaskPlannerService with WorkStateReader with TaskOverviewService, Throwable, Unit] = {
    for {
      taskOverviewService <- ZIO.service[TaskOverviewService]
      workStateReader <- ZIO.service[WorkStateReader]
      taskPlannerService <- ZIO.service[TaskPlannerService]
      jobStateReader <- ZIO.service[JobStateReader]

      // fetch current job state
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

      _ <- ZIO.logDebug(s"APP: Open tasks for planning:")
      _ <- ZIO.logDebug(s"""### TASK RESET TASK:\n${taskResetTasks.mkString("\n")}""")
      _ <- ZIO.logDebug(s"""### TASK PROCESSING TASKS:\n${batchProcessingTasks.mkString("\n")}""")
      _ <- ZIO.logDebug(s"""###JOB TO DONE TASKS:\n${jobToDoneTasks.mkString("\n")}""")

      _ <- taskPlannerService.planTasks(taskResetTasks)
      _ <- taskPlannerService.planTasks(batchProcessingTasks)
      _ <- taskPlannerService.planTasks(jobToDoneTasks)

    } yield ()
  }

  /**
   * Effect taking care of claim and work management
   */
  val taskWorkerApp: ZIO[JobStateReader with WorkStateReader with TaskPlannerService with TaskOverviewService with WorkHandlerService, Throwable, Unit] = {
    for {
      jobStateReader <- ZIO.service[JobStateReader]
      workHandlerService <- ZIO.service[WorkHandlerService]

      // fetch current job state and update processing state for batches
      openJobsState1 <- jobStateReader.fetchOpenJobState
      _ <- workHandlerService.manageBatches(openJobsState1)

      // plan tasks, e.g file claims (if claim-based), move tasks from open to in-progress (as state PLANNED)
      _ <- planTasksEffect

      // fetch current job state and update processing state for batches
      openJobsState2 <- jobStateReader.fetchOpenJobState
      _ <- workHandlerService.manageBatches(openJobsState2)
    } yield ()
  }

  val combinedLayer =
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
      ZioDIConfig.workHandlerServiceLayer >+>
      // configs for metric backends
      ZLayer.succeed(MetricsConfig(5.seconds)) >+>
      // The prometheus reporting layer
      prometheus.publisherLayer >+>
      prometheus.prometheusLayer >+>
      // Default JVM Metrics
      DefaultJvmMetrics.live.unit


  override val run: ZIO[Any, Throwable, Any] = {
    val fixed = Schedule.fixed(30 seconds)
    (for {
      _ <- ZIO.logInfo("Application started!")
      _ <- (taskWorkerApp @@ taskManageCycleInvokeCount).repeat(fixed).fork
      jobStateCache <- ServerEndpoints.openJobStateCache
      _ <- Server.serve(ServerEndpoints.jobPostingEndpoints ++ ServerEndpoints.statusEndpoints(jobStateCache) ++ ServerEndpoints.prometheusEndpoint)
      _ <- ZIO.logInfo("Application is about to exit!")
    } yield ())
      .provide(Server.defaultWithPort(http_server_port) >+> combinedLayer)
  }
}
