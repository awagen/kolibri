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

import de.awagen.kolibri.fleet.zio.config.Directories.InProgressTasks
import de.awagen.kolibri.fleet.zio.config.{AppProperties, Directories}
import de.awagen.kolibri.fleet.zio.io.json.ProcessingStateJsonProtocol.processingStateFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.FileFormats.InProgressTaskFileNameFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{ProcessId, ProcessingState}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import spray.json._
import zio.stream.ZStream
import zio.{Task, ZIO}

import scala.collection.immutable

case class FileStorageWorkStateReader(filterToOverviewReader: (String => Boolean) => DataOverviewReader,
                                      reader: Reader[String, Seq[String]]) extends WorkStateReader {

  private[this] val overviewReader: DataOverviewReader = filterToOverviewReader(_ => true)

  /**
   * Get the mapping of jobId set of high level process descriptors,
   * covering all the passed jobs for the current node.
   * NOTE that the ProcessIds are
   */
  override def getInProgressIdsForNode(jobs: Set[String]): Task[Map[String, Set[ProcessId]]] = ZIO.attemptBlocking {
    jobs.map(jobId => {
      val inProgressStateFiles: Set[String] = overviewReader
        .listResources(InProgressTasks.jobTasksInProgressStateForNodeSubFolder(
          jobId,
          AppProperties.config.node_hash,
          isOpenJob = true
        ), _ => true).toSet
      val processIds = inProgressStateFiles.map(file => InProgressTaskFileNameFormat.processIdFromIdentifier(jobId, file))
      (jobId, processIds)
    }).toMap
  }

  /**
   * Given a processId, parse the respective in-progress state file and return information as ProcessingState.
   */
  private def processIdToProcessState(processId: ProcessId): Task[ProcessingState] = {
    for {
      processFileContent <- ZIO.attemptBlocking(
        reader.read(Directories.InProgressTasks.getInProgressFilePathForJob(processId.jobId, processId.batchNr))
          .mkString("\n")
      )
      processingState <- ZIO.attempt({
        processFileContent.parseJson.convertTo[ProcessingState]
      })
    } yield processingState
  }

  /**
   * Retrieve more detailed information about all batches that are in progress for
   * the passed jobs.
   */
  override def getInProgressStateForNode(jobs: Set[String]): Task[Map[String, Set[ProcessingState]]] = {
    for {
      jobIdToProcessIds <- getInProgressIdsForNode(jobs)
      processingStateMapping <- ZIO.succeed(jobIdToProcessIds)
        .flatMap(mapping => {
          val v: immutable.Iterable[ZIO[Any, Throwable, (String, Set[ProcessingState])]] = mapping.map(x => {
            ZStream.fromIterable(x._2)
              .mapZIO(processId => {
                processIdToProcessState(processId)
                  .onError(e => ZIO.logError(s"Error parsing process state for id: $processId:\n$e"))
              })
              .either
              .filter({
                case Right(_) => true
                case _ => false
              })
              .map(x => x.toOption.get)
              .runCollect
              .map(y => (x._1, y.toSet))
          })
          ZIO.collectAll(v).map(z => z.toMap)
        })
    } yield processingStateMapping
  }
}
