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


package de.awagen.kolibri.fleet.zio.schedule

import de.awagen.kolibri.fleet.zio.config.AppConfig
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.JobStateHandler
import zio._

object Schedules {

  val JSON_FILE_SUFFIX = ".json"

  def findAndRegisterJobs(jobHandler: JobStateHandler): Task[OpenJobsSnapshot] =
    (for {
      _ <- ZIO.logDebug("Start checking for new jobs")
      state <- jobHandler.fetchOpenJobState
    } yield state).logError

  /**
   * Effect for checking file system for specific task-related jobs
   * @param jobFolder - the job subfolder to look into for files
   * @return
   */
  def taskCheckSchedule(jobFolder: String): RIO[Any, Option[Seq[String]]] =
    for {
      _ <- ZIO.logInfo("Starting file check")
      files <- ZIO.attemptBlockingCancelable(AppConfig.persistenceModule.persistenceDIModule
        .dataOverviewReader(fileName => fileName.endsWith(JSON_FILE_SUFFIX))
        .listResources(s"$jobFolder", _ => true))(ZIO.succeed(Option.empty[Seq[String]]))
      _ <- ZIO.logInfo(s"Found files: ${files.mkString(";")}")
    } yield Option.apply(files)

}
