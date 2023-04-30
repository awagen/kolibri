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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.impl

import de.awagen.kolibri.fleet.zio.config.AppConfig
import de.awagen.kolibri.fleet.zio.config.AppProperties.config
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.io.json.JobDefinitionJsonProtocol.JobDefinitionFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.impl.FileStorageJobHandler.JOB_DEFINITION_FILENAME
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.JobHandler
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.zio_di.ZioDIConfig
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import spray.json.JsString
import zio.{Task, ZIO}

object FileStorageJobHandler {

  val JOB_DEFINITION_FILENAME = "job.json"

}

/**
 * Read folder names in jobs base folder and for newly found jobs read the
 * job definition in and add to name -> definition mapping
 */
case class FileStorageJobHandler(overviewReader: DataOverviewReader,
                                 reader: Reader[String, Seq[String]]) extends JobHandler {

  override def registeredJobs: Task[Set[String]] = {
    AppConfig.JobState.jobsInProgress.flatMap(x => x.get)
      .map(x => x.keySet)
  }

  override def newJobs: Task[Seq[String]] = {
    for {
      runningJobs <- registeredJobs
      jobFoldersNames <- ZIO.attemptBlockingIO(overviewReader.listResources(config.jobBaseFolder, _ => true)
        .map(uri => uri.split("/").last).distinct)
      newJobs <- ZIO.attempt(Set(jobFoldersNames:_*).diff(runningJobs).toSeq)
    } yield newJobs

  }

  override def registerNewJobs: Task[Unit] = {
    for {
      jobFolders <- newJobs
      nameToDefMap <- ZIO.attemptBlockingIO({
        jobFolders.map(folder => {
          val jobDefPath = s"${ZioDIConfig.Directories.baseJobFolder(folder)}/$JOB_DEFINITION_FILENAME"
          val jobDefFileContent = reader.read(jobDefPath).mkString("\n")
          val jobDefinition: JobDefinition[_] = JsString(jobDefFileContent).convertTo[JobDefinition[_]]
          (folder, jobDefinition)
        }).toMap
      })
      _ <- ZIO.attempt({
          for {
            map <- AppConfig.JobState.jobsInProgress
            _ <- map.update(_ ++ nameToDefMap)
          } yield ()
        })
    } yield ()
  }
}

