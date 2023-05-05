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

import de.awagen.kolibri.fleet.zio.config.AppProperties.config
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.io.json.JobDefinitionJsonProtocol.JobDefinitionFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.JobStatus.{InvalidJobDefinition, JobState, Loaded}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.JobHandler
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.zio_di.ZioDIConfig
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import org.slf4j.{Logger, LoggerFactory}
import spray.json._
import zio.{IO, Ref, Task, ZIO}

import java.io.IOException

object FileStorageJobHandler {

  val JOB_DEFINITION_FILENAME = "job.json"

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

}

/**
 * Read folder names in jobs base folder and for newly found jobs read the
 * job definition in and add to name -> definition mapping
 */
case class FileStorageJobHandler(overviewReader: DataOverviewReader,
                                 reader: Reader[String, Seq[String]],
                                 writer: Writer[String, String, _],
                                 jobsInProgressRef: Ref[Map[String, JobState]]) extends JobHandler {

  import FileStorageJobHandler._

  /**
   * Return currently registered jobs
   */
  override def registeredJobs: Task[Set[String]] = {
    for {
      _ <- ZIO.logDebug("Retrieving registered jobs")
      inProgressMap <- jobsInProgressRef.get
      _ <- ZIO.logDebug(s"In progress map: $inProgressMap")
      result <- ZIO.succeed(inProgressMap.keySet)
    } yield result
  }

  /**
   * Find new jobs in job directory which are not yet registered
   */
  override def newJobs: Task[Seq[String]] = {
    for {
      _ <- ZIO.logDebug("Start looking for new jobs")
      runningJobs <- registeredJobs
      _ <- ZIO.logDebug(s"Running jobs: $runningJobs")
      jobFoldersNames <- ZIO.attemptBlockingIO(overviewReader.listResources(config.jobBaseFolder, _ => true)
        .map(uri => uri.split("/").last).distinct)
        .logError
      newJobs <- ZIO.attempt(Set(jobFoldersNames: _*).diff(runningJobs).toSeq)
      _ <- ZIO.logInfo(s"Found new jobs: $newJobs")
    } yield newJobs

  }

  /**
   * Derive the file path to the actual job definition for a job
   */
  def jobNameToJobDefinitionFile(jobName: String) =
    s"${ZioDIConfig.Directories.baseJobFolder(jobName)}/$JOB_DEFINITION_FILENAME"

  def jobNameAndBatchNrToBatchFile(jobName: String, batchNr: Int) =
    s"${ZioDIConfig.Directories.jobToOpenTaskSubFolder(jobName)}/$batchNr"

  /**
   * Find yet unregistered jobs and try to parse a JobDefinition out of it
   */
  override def registerNewJobs: Task[Unit] = {
    for {
      _ <- ZIO.logDebug("Starting registering new jobs")
      jobFolders <- newJobs
      _ <- ZIO.logDebug(s"New jobs: $jobFolders")
      nameToDefMap <- ZIO.attemptBlockingIO({
        jobFolders.map(folder => {
          val jobDefPath = jobNameToJobDefinitionFile(folder)
          logger.info(s"Trying to load file: $jobDefPath")
          val jobDefFileContent = reader.read(jobDefPath).mkString("\n")
          logger.info(s"Casting file '$jobDefPath' to job definition")
          var jobState: JobState = InvalidJobDefinition
          try {
            jobState = Loaded(jobDefFileContent.parseJson.convertTo[JobDefinition[_,_]])
            logger.info(s"Finished casting file '$jobDefPath' to job definition")
          }
          catch {
            case e: Exception => logger.warn("Casting file to job definition failed", e)
          }
          (folder, jobState)
        }).toMap
      })
      _ <- ZIO.logDebug(s"New jobs: $nameToDefMap")
      _ <- for {
        _ <- ZIO.logDebug("Updating jobs in progress map")
        _ <- jobsInProgressRef.update(_ ++ nameToDefMap)
      } yield ()
    } yield ()
  }

  override def storeJobDefinition(content: String, jobName: String): IO[IOException, Either[Exception, _]] = {
    ZIO.attemptBlockingIO({
      val writePath = jobNameToJobDefinitionFile(jobName)
      writer.write(content, writePath)
    })
  }

  override def createBatchFilesForJob(jobDefinition: JobDefinition[_,_]): IO[IOException, Unit] = {
    val numBatches = jobDefinition.batches.size
    ZIO.attemptBlockingIO({
      Range(0, numBatches, 1).foreach(batchNr => {
        val fileName = jobNameAndBatchNrToBatchFile(jobDefinition.jobName, batchNr)
        writer.write("", fileName)
      })
    })
  }
}
