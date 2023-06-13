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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader

import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.fleet.zio.config.AppProperties.config
import de.awagen.kolibri.fleet.zio.config.Directories.JobTopLevel.jobNameToJobDefinitionFile
import de.awagen.kolibri.fleet.zio.config.Directories._
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.io.json.JobDefinitionJsonProtocol.JobDefinitionFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives.JobDirective
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.FileFormats.JobDirectoryNameFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.Parts.{CREATION_TIME_IN_MILLIS, JOB_ID}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.{JobStateSnapshot, OpenJobsSnapshot}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates.BatchProcessingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.JobDefinitionLoadStates.{InvalidJobDefinition, JobDefinitionLoadStatus, Loaded}
import de.awagen.kolibri.fleet.zio.utils.FuncUtils
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import org.slf4j.{Logger, LoggerFactory}
import spray.json._
import zio.stream.ZStream
import zio.{Task, URIO, ZIO}

import java.io.IOException

object FileStorageJobStateReader {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private[reader] def jobFolderNameToJobIdAndCreationTimeInMillis(jobDirName: String): (String, Long) = {
    val attributeMap = JobDirectoryNameFormat.parse(jobDirName)
    (attributeMap.get(JOB_ID.namedClassTyped.name).get,
      attributeMap.get(CREATION_TIME_IN_MILLIS.namedClassTyped.name).get)
  }

  private[reader] def castableAsInt(str: String): Boolean = {
    FuncUtils.isExecutableStringOp[Int](x => x.toInt)(str)
  }

}

/**
 * Read folder names in jobs base folder and for newly found jobs read the
 * job definition in and add to name -> definition mapping.
 */
case class FileStorageJobStateReader(overviewReader: DataOverviewReader,
                                     reader: Reader[String, Seq[String]]) extends JobStateReader {

  import FileStorageJobStateReader._

  private[reader] def loadJobDefinitionByJobDirectoryName(jobDirName: String): JobDefinitionLoadStatus = {
    val jobDefPath = jobNameToJobDefinitionFile(jobDirName)
    val jobDefFileContent = reader.read(jobDefPath).mkString("\n")
    var jobState: JobDefinitionLoadStatus = InvalidJobDefinition
    try {
      jobState = Loaded(jobDefFileContent.parseJson.convertTo[JobDefinition[_, _, _ <: WithCount]])
      logger.info(s"Finished casting file '$jobDefPath' to job definition")
    }
    catch {
      case e: Exception => logger.warn("Casting file to job definition failed", e)
    }
    jobState
  }

  private[reader] def loadJobLevelDirectivesByJobDirectoryName(jobDirName: String): Set[JobDirective] = {
    val directory = JobTopLevel.folderForJob(jobDirName, isOpenJob = true)
    overviewReader.listResources(directory, x => x.split("/").last.startsWith(JobDirectives.JOB_DIRECTIVE_PREFIX))
      .map(x => x.split("/").last).map(JobDirective.parse).toSet
  }

  /**
   * Retrieves all batches for a job with their respective current status.
   * NOTE: right now assumes that all files that are named as any integer are actually a batch file.
   * Might wanna change this for a proper formatting with predefined prefix.
   */
  private[this] def findBatchesInFolderAsState(folder: String, asState: BatchProcessingStatus): Map[Int, BatchProcessingStatus] = {
    overviewReader
      .listResources(folder, _ => true)
      .map(x => x.split("/").last)
      .filter(castableAsInt)
      .map(x => (x.toInt, asState))
      .toMap
  }

  /**
   * In in-progress base folder first find subfolders, corresponding to the processing
   * node hashes, and then look in each node subfolder for in-progress files and
   * extract the respective batchIds
   */
  private[this] def findInProgressBatches(baseFolder: String): Set[Int] = {
    val nodeHashes = overviewReader
      .listResources(baseFolder, _ => true)
      .map(x => x.split("/").last)
    nodeHashes
      .map(hash => {
        findBatchesInFolderAsState(s"${baseFolder.stripSuffix("/")}/$hash", BatchProcessingStates.InProgress).keySet
      }).toSet.flatten
  }

  /**
   * For given job folder name, retrieve current state of batch to processing state mapping
   */
  private[this] def findBatchesForJobWithState(jobDirName: String): Map[Int, BatchProcessingStatus] = {
    val openBatchMap = findBatchesInFolderAsState(OpenTasks.jobOpenTasksSubFolder(jobDirName, isOpenJob = true), BatchProcessingStates.Open)
    val inProgressBatchMap = findInProgressBatches(InProgressTasks.jobTasksInProgressStateSubFolder(jobDirName, isOpenJob = true))
      .map(batch => (batch, BatchProcessingStates.InProgress)).toMap
    val doneBatchMap = findBatchesInFolderAsState(DoneTasks.jobDoneTasksSubFolder(jobDirName, isOpenJob = true), BatchProcessingStates.Done)
    openBatchMap ++ inProgressBatchMap ++ doneBatchMap
  }

  /**
   * Given a directory name, fetch info and wrap in JobStateSnapshot
   */
  private[this] def retrieveOpenJobStateSnapshot(jobDirName: String): URIO[Any, Either[Throwable, JobStateSnapshot]] =
    (for {
      jobNameAndCreationTime <- ZIO.attempt(jobFolderNameToJobIdAndCreationTimeInMillis(jobDirName))
      jobDefinition <- ZIO.attemptBlocking(loadJobDefinitionByJobDirectoryName(jobDirName))
        .flatMap({
          case InvalidJobDefinition => ZIO.fail(new RuntimeException("invalid job definition format"))
          case Loaded(definition) => ZIO.succeed(definition)
        })
      jobLevelDirectives <- ZIO.attemptBlocking(loadJobLevelDirectivesByJobDirectoryName(jobDirName))
      batchStateMapping <- ZIO.attemptBlocking(findBatchesForJobWithState(jobDirName))
    } yield JobStateSnapshot(
      jobDirName,
      jobNameAndCreationTime._2,
      jobDefinition,
      jobLevelDirectives,
      batchStateMapping
    )).logError.either

  private[this] def retrieveAllOpenJobStateSnapshots: ZIO[Any, IOException, Seq[JobStateSnapshot]] = {
    val jobResults: ZIO[Any, IOException, Seq[Either[Throwable, JobStateSnapshot]]] = for {
      // retrieve job folder names
      jobFolderNames <- ZIO.attemptBlockingIO[Seq[String]](overviewReader.listResources(config.openJobBaseFolder, _ => true)
        .map(uri => uri.split("/").last).distinct)
        .logError
      _ <- ZIO.logInfo(s"job folder names: $jobFolderNames")
      // parse details out of the folder names (jobName_timeInMillis)
      jobStateSnapshots <- ZStream.fromIterable(jobFolderNames)
        .mapZIO(folderName => retrieveOpenJobStateSnapshot(folderName))
        .runFold[Seq[Either[Throwable, JobStateSnapshot]]](Seq.empty)((oldSeq, newElement) => oldSeq :+ newElement)
    } yield jobStateSnapshots
    jobResults.map(x => x.filter({
      case Left(_) => false
      case Right(_) => true
    }).map(y => y.toOption.get))

  }

  /**
   * Schedule task that can be run via
   * Runtime.default.run(handler.updateSchedule).fork.
   * Logic shall contain all updates of
   * - available jobs sorted by priority and mapped to their definitions
   * - set job level directives
   * - open jobs
   */
  override def fetchOpenJobState: Task[OpenJobsSnapshot] = {
    for {
      jobStateSnapshots <- retrieveAllOpenJobStateSnapshots
    } yield OpenJobsSnapshot(jobStateSnapshots.map(x => (x.jobId, x)).toMap)

  }

}
