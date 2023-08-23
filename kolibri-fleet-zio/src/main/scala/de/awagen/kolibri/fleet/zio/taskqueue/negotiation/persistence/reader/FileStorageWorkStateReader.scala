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


case class FileStorageWorkStateReader(filterToOverviewReader: (String => Boolean) => DataOverviewReader,
                                      reader: Reader[String, Seq[String]]) extends WorkStateReader {

  private[this] val overviewReader: DataOverviewReader = filterToOverviewReader(_ => true)

  /**
   * Get the mapping of jobId set of high level process descriptors,
   * covering all the passed jobs for the current node.
   * If resources for a job cannot be found, there will be no entry for the corresponding job in the result
   */
  private[reader] def getInProgressIdsForNode(jobs: Set[String], nodeHash: String): Task[Map[String, Set[ProcessId]]] = {
    ZStream.fromIterable(jobs)
      .mapZIO(jobId => ZIO.attemptBlocking {
        val subFolder = InProgressTasks.jobTasksInProgressStateForNodeSubFolder(jobId, nodeHash, isOpenJob = true)
        val inProgressStateFiles: Set[String] = overviewReader.listResources(subFolder, _ => true).toSet
        val processIds = inProgressStateFiles.map(file => InProgressTaskFileNameFormat.processIdFromIdentifier(jobId, file))
        (jobId, processIds)
      }
        .either)
      .filterZIO({
        case Right(_) => ZIO.succeed(true)
        case Left(e) =>
          ZIO.logError(s"Error on retrieving processing state folder overview:\n${e.getMessage}") *>
            ZIO.succeed(false)
      })
      .map(x => x.toOption.get)
      .runCollect.map(x => x.toMap)
  }

  /**
   * Get the mapping of jobId to processIds for the current node
   */
  override def getInProgressIdsForCurrentNode(jobs: Set[String]): Task[Map[String, Set[ProcessId]]] = {
    getInProgressIdsForNode(jobs, AppProperties.config.node_hash)
  }

  /**
   * For the passed jobIds, collect the set of nodeHashes for which in-progress sub-folders exist for any
   * of the passed jobsIds.
   */
  private[reader] def getAllNodeHashesWithInProgressStates(jobs: Set[String]): Task[Set[String]] = {
    ZStream.fromIterable(jobs)
      .map(jobId => InProgressTasks.jobTasksInProgressStateSubFolder(jobId, isOpenJob = true))
      .map(subFolder => overviewReader.listResources(subFolder, _ => true).map(x => x.split("/").last).toSet)
      .runFold(Set.empty[String])((oldSet, newSet) => oldSet ++ newSet)
  }

  /**
   * Retrieve mapping of nodeHash (identifying the processing node) to map of jobId -> Set[ProcessId]
   */
  override def getInProgressIdsForAllNodes(jobs: Set[String]): Task[Map[String, Map[String, Set[ProcessId]]]] = {
    for {
      activeNodeHashes <- getAllNodeHashesWithInProgressStates(jobs)
      results <- ZStream.fromIterable(activeNodeHashes)
        .mapZIO(nodeHash => getInProgressIdsForNode(jobs, nodeHash).map(x => Map(nodeHash -> x)))
        .runFold(Map.empty[String, Map[String, Set[ProcessId]]])((oldMap, newEntry) => oldMap ++ newEntry)
    } yield results
  }

  /**
   * Given a processId, parse the respective in-progress state file and return information as ProcessingState.
   * Note that in case the file system doesnt see the respective file,
   * there wont be a processing state value, hus return value is Option here.
   */
  override def processIdToProcessState(processId: ProcessId, nodeHash: String): Task[Option[ProcessingState]] = {
    for {
      inProgressFilePath <- ZIO.succeed(Directories.InProgressTasks.getInProgressFilePathForJob(processId.jobId,
        processId.batchNr, nodeHash))
      _ <- ZIO.logDebug(s"Trying to read in-progress state file: $inProgressFilePath")
      processFileContentOpt <- ZIO.attemptBlockingIO({
        Some(reader.read(inProgressFilePath).mkString("\n"))
      }).catchAll(ex =>
        ZIO.logDebug(s"Could not read in-progress file for processId '$processId':\n$ex")
          *> ZIO.succeed(None)
      )
      processingState <- ZIO.attempt({
        processFileContentOpt.map(x => x.parseJson.convertTo[ProcessingState])
      })
    } yield processingState
  }

  /**
   * Given a set of processIds, read the ProcessingState for each and return those for which the
   * ProcessingState could be derived (result only contains those, those unsuccessful are ignored
   */
  private[reader] def processIdsToProcessState(processIds: Seq[ProcessId], nodeHash: String): Task[Seq[ProcessingState]] = {
    ZStream.fromIterable(processIds)
      .mapZIO(processId => {
        processIdToProcessState(processId, nodeHash)
          .onError(e => ZIO.logError(s"Error parsing process state for id: $processId:\n$e"))
      })
      .either
      .filter({
        case Right(_) => true
        case _ => false
      })
      .map(x => x.toOption.get)
      .runCollect
      .map(x => x.filter(y => y.nonEmpty).map(y => y.get))
  }

  /**
   * Retrieve more detailed information about all batches that are in progress for
   * the passed jobs.
   */
  override def getInProgressStateForCurrentNode(jobs: Set[String]): Task[Map[String, Set[ProcessingState]]] = {
    for {
      jobIdToProcessIds <- getInProgressIdsForCurrentNode(jobs)
      processingStateMapping <- ZStream.fromIterable(jobIdToProcessIds)
        .mapZIO(x => processIdsToProcessState(x._2.toSeq, AppProperties.config.node_hash).map(y => (x._1, y.toSet)))
        .runCollect.map(y => y.toMap)
    } yield processingStateMapping
  }

  /**
   * For all nodeHashes for which there exist an in-progress directory in any of the passed jobs,
   * retrieve the corresponding set of ProcessingState per jobId.
   * Thus the mapping is nodeHash -> (jobId -> Set[ProcessingState]).
   */
  override def getInProgressStateForAllNodes(jobs: Set[String]): Task[Map[String, Map[String, Set[ProcessingState]]]] = {
    for {
      nodeHashToJobIdToProcessIds <- getInProgressIdsForAllNodes(jobs)
      nodeHashToProcessingStatesMapping <- ZStream.fromIterable(nodeHashToJobIdToProcessIds.keySet)
        .mapZIO(hash => {
          ZStream.fromIterable(nodeHashToJobIdToProcessIds(hash))
            .mapZIO(x => processIdsToProcessState(x._2.toSeq, hash).map(y => (x._1, y.toSet)))
            .runCollect.map(y => (hash, y.toMap))
        })
        .runCollect.map(x => x.toMap)
    } yield nodeHashToProcessingStatesMapping
  }
}
