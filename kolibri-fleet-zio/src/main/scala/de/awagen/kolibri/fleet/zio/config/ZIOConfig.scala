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
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.impl.FileStorageJobHandler
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.JobStatus.JobState
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.JobHandler
import zio.{Queue, Ref, UIO, ZIO}

/**
 * Config to setup some global instances created via ZIO effects.
 */
class ZIOConfig {

  private[this] var runningJobsRef: Ref[Map[String, JobState]] = null
  private[this] var jobHandler: JobHandler = null
  private[this] var jobQueue: Queue[JobDefinition[_]] = null

  def getRunningJobs: UIO[Map[String, JobState]] = runningJobsRef.get
  def getJobHandler: JobHandler = jobHandler
  def getJobQueue: Queue[JobDefinition[_]] = null

  def init(): ZIO[Any, Throwable, Unit] = {
    ZIO.when(runningJobsRef == null) {
      for {
        _ <- ZIO.logDebug("Running ZIOConfig init")
        runningJobs <- Ref.make(Map.empty[String, JobState])
        _ <- ZIO.attempt({
          runningJobsRef = runningJobs
        })
        queue <- Queue.bounded[JobDefinition[_]](5)
        _ <- ZIO.attempt({
          jobQueue = queue
        })
        _ <- ZIO.attempt({
          jobHandler = FileStorageJobHandler(
            AppConfig.persistenceModule.persistenceDIModule.dataOverviewReader(_ => true),
            AppConfig.persistenceModule.persistenceDIModule.reader,
            runningJobsRef)
        })
        _ <- ZIO.logDebug(s"runningJobsRef: $runningJobsRef")
        _ <- ZIO.logDebug(s"jobHandler: $jobHandler")
      } yield ()
    }.orElse(for {
      _ <- ZIO.logInfo("already initialized, skipping init()")
    } yield ()).logError.map(_ => ())
  }

}
