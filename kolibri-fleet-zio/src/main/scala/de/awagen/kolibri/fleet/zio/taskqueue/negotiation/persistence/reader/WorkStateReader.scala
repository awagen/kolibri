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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{ProcessId, ProcessingState}
import zio.Task


trait WorkStateReader {

  /**
   * Get the mapping of jobId to full identifiers (e.g in case of file based
   * mechanism this would be the file name) of in-progress files
   * for the current node, covering all the passed jobs.
   */
  def getInProgressIdsForCurrentNode(jobs: Set[String]): Task[Map[String, Set[ProcessId]]]

  def getInProgressIdsForAllNodes(jobs: Set[String]): Task[Map[String, Map[String, Set[ProcessId]]]]

  /**
   * Retrieve more detailed information about all batches that are in progress for
   * the passed jobs.
   */
  def getInProgressStateForCurrentNode(jobs: Set[String]): Task[Map[String, Set[ProcessingState]]]

  def getInProgressStateForAllNodes(jobs: Set[String]): Task[Map[String, Map[String, Set[ProcessingState]]]]

  /**
   * Given a processId, retrieve the current ProcessingState
   */
  def processIdToProcessState(processId: ProcessId, nodeHash: String): Task[Option[ProcessingState]]

}
