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


package de.awagen.kolibri.fleet.zio.config

import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.impl.FileStorageJobStateHandler
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.JobDefinitionLoadStates.JobDefinitionLoadStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.JobStateHandler
import zio.{Queue, Ref, UIO, ZIO}

/**
 * Config to setup some global instances created via ZIO effects.
 */
class ZIOConfig {

  private[this] var runningJobsRef: Ref[Map[String, JobDefinitionLoadStatus]] = null
  private[this] var jobHandler: JobStateHandler = null
  private[this] var jobQueue: Queue[JobDefinition[_,_]] = null

  // TODO: do not need this specific one, will replace with other structures to record
  // a) the queue for claimed batches and b) the batches currently in progress together with some
  // status on their progress (e.g via information using the aggregator on each task)
  def getRunningJobs: UIO[Map[String, JobDefinitionLoadStatus]] = runningJobsRef.get

  def getJobHandler: JobStateHandler = jobHandler

  def getJobQueue: Queue[JobDefinition[_,_]] = null

  def init(): ZIO[Any, Throwable, Unit] = {
    ZIO.ifZIO(ZIO.succeed(runningJobsRef == null))(
      onTrue = {
        for {
          _ <- ZIO.logDebug("Running ZIOConfig init")
          runningJobs <- Ref.make(Map.empty[String, JobDefinitionLoadStatus])
          _ <- ZIO.attempt({
            runningJobsRef = runningJobs
          })
          queue <- Queue.bounded[JobDefinition[_,_]](5)
          _ <- ZIO.attempt({
            jobQueue = queue
          })
          _ <- ZIO.attempt({
            jobHandler = FileStorageJobStateHandler(
              AppConfig.persistenceModule.persistenceDIModule.dataOverviewReader(_ => true),
              AppConfig.persistenceModule.persistenceDIModule.reader,
              AppConfig.persistenceModule.persistenceDIModule.writer)
          })
          _ <- ZIO.logDebug(s"runningJobsRef: $runningJobsRef")
          _ <- ZIO.logInfo(s"jobHandler: $jobHandler")
        } yield ()
      },
      onFalse = for {
        _ <- ZIO.logInfo("already initialized, skipping init()")
      } yield ()
    ).logError.map(_ => ())
  }

}
