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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer

import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.fleet.zio.config.AppProperties.config
import de.awagen.kolibri.fleet.zio.config.Directories.{JobTopLevel, OpenTasks}
import de.awagen.kolibri.fleet.zio.config.Directories.JobTopLevel.jobNameToJobDefinitionFile
import de.awagen.kolibri.fleet.zio.config.Directories.OpenTasks.jobNameAndBatchNrToBatchFile
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.io.json.JobDefinitionJsonProtocol.JobDefinitionFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives.JobDirective
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.JobStateReader
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessId
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import spray.json._
import zio.stream.ZStream
import zio.{Task, ZIO}

case class FileStorageJobStateWriter(writer: Writer[String, String, _],
                                     jobStateReader: JobStateReader) extends JobStateWriter {

  /**
   * Move full job directory from folder for jobs to be completed to folder containing completed jobs
   */
  override def moveToDone(jobDirectoryName: String): Task[Unit] = {
    ZIO.attemptBlockingIO({
      val jobProcessingDir = JobTopLevel.folderForJob(jobDirectoryName, isOpenJob = true)
      writer.moveDirectory(jobProcessingDir, config.doneJobBaseFolder)
    })
  }

  override def storeJobDefinitionAndBatches(jobDefinition: String, jobSubFolder: String): Task[Unit] = {
    for {
      jobDef <- ZIO.attempt(jobDefinition.parseJson.convertTo[JobDefinition[_, _, _ <: WithCount]])
      _ <- storeJobDefinition(jobDefinition, jobSubFolder)
      batchStorageResult <- storeBatchFilesForJob(jobDef, jobSubFolder)
    } yield batchStorageResult
  }

  private[this] def storeJobDefinition(jobDefinition: String, jobSubFolder: String): Task[Unit] = {
    ZIO.attemptBlockingIO({
      val writePath = jobNameToJobDefinitionFile(jobSubFolder)
      writer.write(jobDefinition, writePath)
    }).flatMap({
      case Left(e) => ZIO.fail(e)
      case Right(v) => ZIO.succeed(v)
    })
  }

  private[this] def storeBatchFilesForJob(jobDefinition: JobDefinition[_, _, _], jobSubFolder: String): Task[Unit] = {
    ZIO.attemptBlockingIO({
      val numBatches = jobDefinition.batches.size
      Range(0, numBatches, 1).foreach(batchNr => {
        val fileName = jobNameAndBatchNrToBatchFile(jobSubFolder, batchNr)
        writer.write("", fileName)
      })
    })
  }

  override def writeBatchToOpen(processId: ProcessId): Task[Unit] = {
    ZIO.attemptBlockingIO({
      val writePath = OpenTasks.jobNameAndBatchNrToBatchFile(processId.jobId, processId.batchNr)
      writer.write("", writePath)
    }).flatMap({
      case Left(e) => ZIO.fail(e)
      case Right(v) => ZIO.succeed(v)
    })
  }

  /**
   * Remove all directives currently present for the jobId
   */
  override def removeAllDirectives(jobId: String): Task[Unit] = {
    for {
      directives <- ZIO.attempt(jobStateReader.loadJobLevelDirectivesByJobDirectoryName(jobId))
      _ <- ZIO.logInfo(s"Found directives for deletion for jobId '$jobId': $directives")
      _ <- removeDirectives(jobId, directives)
    } yield ()
  }

  /**
   * Remove passed directives for the given jobId
   */
  override def removeDirectives(jobId: String, directives: Set[JobDirectives.JobDirective]): Task[Unit] = {
    ZStream.fromIterable(directives)
      .mapZIO(directive => {
        for {
          directiveFile <- ZIO.succeed(s"${JobTopLevel.folderForJob(jobId, isOpenJob = true)}/${JobDirective.toString(directive)}")
          _ <- ZIO.logInfo(s"Trying to delete job directive file: $directiveFile")
          deleteResult <- ZIO.attempt(writer.delete(directiveFile))
          _ <- ZIO.whenCase(deleteResult)({
            case Right(_) =>
              ZIO.logInfo(s"Succeeded deleting job directive file '$directiveFile''")
            case Left(throwable) =>
              ZIO.logWarning(s"Failed deleting job directive file '$directiveFile':\nexception: $throwable")
          })
        } yield ()
      })
      .runDrain
  }

  /**
   * Write passed directives for the given jobId
   */
  override def writeDirectives(jobId: String, directives: Set[JobDirectives.JobDirective]): Task[Unit] = {
    for {
      jobTopLevelDirectory <- ZIO.succeed(s"${JobTopLevel.folderForJob(jobId, isOpenJob = true)}")
      _ <- ZStream.fromIterable(directives)
        .foreach(directive => {
          for {
            writePath <- ZIO.succeed(s"$jobTopLevelDirectory/${JobDirective.toString(directive)}")
            _ <- ZIO.logInfo(s"Trying to write directive to path '$writePath''")
            writeResult <- ZIO.attempt(writer.write("", writePath))
            _ <- ZIO.whenCase(writeResult)({
              case Right(_) =>
                ZIO.logInfo(s"Succeeded writing job directive file '$writePath''")
              case Left(throwable) =>
                ZIO.logWarning(s"Failed deleting job directive file '$writePath':\nexception: $throwable")
            })
          } yield ()
        })
    } yield ()
  }

  override def removeJobFolder(jobId: String, isOpenJob: Boolean): Task[Unit] = {
    for {
      jobTopLevelDirectory <- ZIO.succeed(s"${JobTopLevel.folderForJob(jobId, isOpenJob = isOpenJob)}")
      _ <- ZIO.logInfo(s"Trying to delete folder: $jobTopLevelDirectory")
      _ <- ZIO.attemptBlockingIO(writer.deleteDirectory(jobTopLevelDirectory))
    } yield ()
  }
}
