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

import de.awagen.kolibri.datatypes.mutable.stores.{TypedMapStore, WeaklyTypedMap}
import de.awagen.kolibri.fleet.zio.config.AppProperties.config
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.io.json.JobDefinitionJsonProtocol.JobDefinitionFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives.JobDirective
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.FileNameFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.Parts.{BATCH_NR, CREATION_TIME_IN_MILLIS, JOB_ID, NODE_HASH, TOPIC}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.impl.FileStorageClaimHandler.ClaimFileNameFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{JobStateSnapshot, OpenJobsSnapshot}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates.BatchProcessingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.JobDefinitionLoadStates.{InvalidJobDefinition, JobDefinitionLoadStatus, Loaded}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.ClaimHandler.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.ClaimHandler.ClaimTopic.{ClaimTopic, UNKNOWN}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.JobStateHandler
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.zio_di.ZioDIConfig
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.zio_di.ZioDIConfig.Directories
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json._
import zio.stream.ZStream
import zio.{Task, URIO, ZIO}

import java.io.IOException
import scala.collection.mutable

object FileStorageJobStateHandler {

  val JOB_DEFINITION_FILENAME = "job.json"

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  case object JobDirectoryNameFormat extends FileNameFormat(Seq(JOB_ID, CREATION_TIME_IN_MILLIS), "_") {
    def getFileName(batchNr: Int, creationTimeInMillis: Long): String = {
      this.format(TypedMapStore(mutable.Map(
        BATCH_NR.namedClassTyped -> batchNr,
        CREATION_TIME_IN_MILLIS.namedClassTyped -> creationTimeInMillis,
      )))
    }
  }

  /**
   * Derive the file path to the actual job definition for a job
   */
  private[impl] def jobNameToJobDefinitionFile(jobName: String) =
    s"${ZioDIConfig.Directories.folderForJob(jobName, isOpenJob = true)}/$JOB_DEFINITION_FILENAME"

  private[impl] def jobNameAndBatchNrToBatchFile(jobName: String, batchNr: Int) =
    s"${ZioDIConfig.Directories.jobOpenTasksSubFolder(jobName, isOpenJob = true)}/$batchNr"

  private[impl] def jobFolderNameToJobIdAndCreationTimeInMillis(jobDirName: String): (String, Long) = {
    val attributeMap = JobDirectoryNameFormat.parse(jobDirName)
    (attributeMap.get(JOB_ID.namedClassTyped.name).get,
      attributeMap.get(CREATION_TIME_IN_MILLIS.namedClassTyped.name).get)
  }

  private[impl] def castableAsInt(str: String): Boolean = {
    try {
      str.toInt
      true
    }
    catch {
      case _: Exception =>
        false
    }
  }

}

/**
 * Read folder names in jobs base folder and for newly found jobs read the
 * job definition in and add to name -> definition mapping
 */
case class FileStorageJobStateHandler(overviewReader: DataOverviewReader,
                                      reader: Reader[String, Seq[String]],
                                      writer: Writer[String, String, _]) extends JobStateHandler {

  import FileStorageJobStateHandler._

  private[impl] def loadJobDefinitionByJobDirectoryName(jobDirName: String): JobDefinitionLoadStatus = {
    val jobDefPath = jobNameToJobDefinitionFile(jobDirName)
    val jobDefFileContent = reader.read(jobDefPath).mkString("\n")
    var jobState: JobDefinitionLoadStatus = InvalidJobDefinition
    try {
      jobState = Loaded(jobDefFileContent.parseJson.convertTo[JobDefinition[_, _, _]])
      logger.info(s"Finished casting file '$jobDefPath' to job definition")
    }
    catch {
      case e: Exception => logger.warn("Casting file to job definition failed", e)
    }
    jobState
  }

  private[impl] def loadJobLevelDirectivesByJobDirectoryName(jobDirName: String): Set[JobDirective] = {
    val directory = ZioDIConfig.Directories.folderForJob(jobDirName, isOpenJob = true)
    overviewReader.listResources(directory, x => x.split("/").last.startsWith(JobDirectives.JOB_DIRECTIVE_PREFIX))
      .map(x => x.split("/").last).map(JobDirective.parse).toSet
  }

  override def storeJobDefinitionAndBatches(jobDefinition: String): Task[Unit] = {
    for {
      jobDef <- ZIO.attempt(jobDefinition.parseJson.convertTo[JobDefinition[_, _, _]])
      _ <- storeJobDefinition(jobDefinition, s"${jobDef.jobName}_${System.currentTimeMillis()}")
      batchStorageResult <- storeBatchFilesForJob(jobDef)
    } yield batchStorageResult
  }

  private[this] def storeJobDefinition(jobDefinition: String, jobName: String): Task[Unit] = {
    ZIO.attemptBlockingIO({
      val writePath = jobNameToJobDefinitionFile(jobName)
      writer.write(jobDefinition, writePath)
    }).flatMap({
      case Left(e) => ZIO.fail(e)
      case Right(v) => ZIO.succeed(v)
    })
  }

  private[this] def storeBatchFilesForJob(jobDefinition: JobDefinition[_, _, _]): Task[Unit] = {
    ZIO.attemptBlockingIO({
      val numBatches = jobDefinition.batches.size
      Range(0, numBatches, 1).foreach(batchNr => {
        val fileName = jobNameAndBatchNrToBatchFile(jobDefinition.jobName, batchNr)
        writer.write("", fileName)
      })
    })
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
   * For given job folder name, retrieve current state of batch to processing state mapping
   */
  private[this] def findBatchesForJobWithState(jobDirName: String): Map[Int, BatchProcessingStatus] = {
    val openBatchMap = findBatchesInFolderAsState(Directories.jobOpenTasksSubFolder(jobDirName, isOpenJob = true), BatchProcessingStates.Open)
    val inProgressBatchMap = findBatchesInFolderAsState(Directories.jobTasksInProgressStateSubFolder(jobDirName, isOpenJob = true), BatchProcessingStates.InProgress)
    val doneBatchMap = findBatchesInFolderAsState(Directories.jobDoneTasksSubFolder(jobDirName, isOpenJob = true), BatchProcessingStates.Done)
    openBatchMap ++ inProgressBatchMap ++ doneBatchMap
  }

  /**
   * Get all full claim paths for the passed jobId and the given claimTopic
   * TODO: duplicate of the same in FileStorageClaimHandler. Externalize to io-service
   */
  private def getExistingClaimsForJob(jobId: String, claimTopic: ClaimTopic): Seq[String] = {
    overviewReader
      .listResources(Directories.jobClaimSubFolder(jobId, isOpenJob = true), filename => {
        val topic: String = ClaimFileNameFormat.parse(filename.split("/").last)
          .get(TOPIC.namedClassTyped.name)
          .getOrElse(UNKNOWN.toString)
        topic == claimTopic.toString
      })
  }

  /**
   * Given a directory name, fetch info and wrap in JobStateSnapshot
   */
  private[this] def retrieveJobStateSnapshot(jobDirName: String): URIO[Any, Either[Throwable, JobStateSnapshot]] =
    (for {
      jobNameAndCreationTime <- ZIO.attempt(jobFolderNameToJobIdAndCreationTimeInMillis(jobDirName))
      jobDefinition <- ZIO.attemptBlocking(loadJobDefinitionByJobDirectoryName(jobDirName))
        .flatMap({
          case InvalidJobDefinition => ZIO.fail(new RuntimeException("invalid job definition format"))
          case Loaded(definition) => ZIO.succeed(definition)
        })
      jobLevelDirectives <- ZIO.attemptBlocking(loadJobLevelDirectivesByJobDirectoryName(jobDirName))
      batchStateMapping <- ZIO.attemptBlocking(findBatchesForJobWithState(jobDirName))
      // Retrieve the information about existing claims per batch for the current job
      claimsForJob <- ZIO.attemptBlocking(getExistingClaimsForJob(jobDirName, ClaimTopic.JOB_TASK_PROCESSING_CLAIM)
        .map(claimPath => {
          val claimInfo: WeaklyTypedMap[String] = ClaimFileNameFormat.parse(claimPath.split("/").last)
          val claimingNodeHash: String = claimInfo.get(NODE_HASH.namedClassTyped.name).getOrElse("")
          val batchNr: Int = claimInfo.get(BATCH_NR.namedClassTyped.name).getOrElse(-1)
          (batchNr, claimingNodeHash)
        })
        .foldLeft(Map.empty[Int, Set[String]])((oldMap, tuple) => {
          oldMap + (tuple._1 -> (oldMap.getOrElse(tuple._1, Set.empty[String]) + tuple._2))
        })
      )
    } yield JobStateSnapshot(
      jobNameAndCreationTime._1,
      jobNameAndCreationTime._2,
      jobDefinition,
      jobLevelDirectives,
      batchStateMapping,
      claimsForJob
    )).logError.either

  private[this] def retrieveJobStateSnapshots: ZIO[Any, IOException, Seq[JobStateSnapshot]] = {
    val jobResults: ZIO[Any, IOException, Seq[Either[Throwable, JobStateSnapshot]]] = for {
      // retrieve job folder names
      jobFolderNames <- ZIO.attemptBlockingIO[Seq[String]](overviewReader.listResources(config.openJobBaseFolder, _ => true)
        .map(uri => uri.split("/").last).distinct)
        .logError
      _ <- ZIO.logInfo(s"job folder names: $jobFolderNames")
      // parse details out of the folder names (jobName_timeInMillis)
      jobStateSnapshots <- ZStream.fromIterable(jobFolderNames)
        .mapZIO(folderName => retrieveJobStateSnapshot(folderName))
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
      jobStateSnapshots <- retrieveJobStateSnapshots
    } yield OpenJobsSnapshot(jobStateSnapshots.map(x => (x.jobId, x)).toMap)

  }

  /**
   * Move full job directory from folder for jobs to be completed to folder containing completed jobs
   */
  override def moveToDone(jobDirectoryName: String): Task[Unit] = {
    ZIO.attemptBlockingIO({
      val jobProcessingDir = ZioDIConfig.Directories.folderForJob(jobDirectoryName, isOpenJob = true)
      writer.moveDirectory(jobProcessingDir, config.doneJobBaseFolder)
    })
  }

}
