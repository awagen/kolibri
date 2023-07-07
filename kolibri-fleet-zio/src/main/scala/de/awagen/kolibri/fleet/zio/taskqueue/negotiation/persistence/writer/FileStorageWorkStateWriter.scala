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

import de.awagen.kolibri.fleet.zio.config.{AppProperties, Directories}
import de.awagen.kolibri.fleet.zio.io.json.ProcessingStateJsonProtocol
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{ProcessId, ProcessingState}
import de.awagen.kolibri.storage.io.reader.Reader
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import zio.{Task, ZIO}

case class FileStorageWorkStateWriter(reader: Reader[String, Seq[String]], writer: Writer[String, String, _]) extends WorkStateWriter {

  override def updateInProgressState(processingState: ProcessingState): Task[Unit] = {
    ZIO.attemptBlocking({
      val file = Directories.InProgressTasks.getInProgressFilePathForJob(
        processingState.stateId.jobId,
        processingState.stateId.batchNr,
        AppProperties.config.node_hash
      )
      val processingFileStr = ProcessingStateJsonProtocol.processingStateFormat.write(processingState).toString()
      writer.write(processingFileStr, file)
    }).flatMap(res => ZIO.fromEither(res))
      .map(_ => ())
  }

  override def deleteInProgressState(processId: ProcessId, nodeHash: String): Task[Unit] = {
    ZIO.attemptBlocking({
      val file = Directories.InProgressTasks.getInProgressFilePathForJob(processId.jobId, processId.batchNr, nodeHash)
      writer.delete(file)
    }).flatMap(res => ZIO.fromEither(res))
      .map(_ => ())
  }

  override def writeToDone(processId: ProcessId): Task[Unit] = {
    ZIO.attemptBlocking({
      val file = Directories.DoneTasks.jobNameAndBatchNrToDoneFile(processId.jobId, processId.batchNr, isOpenJob = true)
      writer.write("", file)
    }).flatMap(res => ZIO.fromEither(res))
      .map(_ => ())
  }

  /**
   * Move in-progress state file to done
   */
  override def moveToDone(processId: ProcessId, nodeHash: String): Task[Unit] = {
    for {
      currentFileURI <- ZIO.attempt(Directories.InProgressTasks.getInProgressFilePathForJob(processId.jobId, processId.batchNr, nodeHash))
      currentState <- ZIO.attemptBlockingIO(reader.read(currentFileURI).mkString("\n"))
      doneFileURI <- ZIO.attempt(Directories.DoneTasks.jobNameAndBatchNrToDoneFile(processId.jobId, processId.batchNr, isOpenJob = true))
      _ <- ZIO.attemptBlockingIO(writer.write(currentState, doneFileURI))
      _ <- ZIO.attemptBlockingIO(writer.delete(currentFileURI))
    } yield ()
  }

}
