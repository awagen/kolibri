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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives.JobDirective
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessId
import zio.Task

trait JobStateWriter {

  /**
   * Move a job from open to done
   */
  def moveToDone(jobName: String): Task[Unit]

  /**
   * Storing the job definition content.
   * Storing batch info, and so that each batch can be claimed for processing.
   * After writing job definition and batches, write PROCESS directive into
   * folder to indicate the job is up for processing.
   */
  def storeJobDefinitionAndBatches(jobDefinition: String, jobSubFolder: String): Task[Unit]

  /**
   * Remove a whole folder corresponding to a particular jobId.
   * Depending on the value of isOpenJob, the particular folder will
   * either be deleted in the current 'open' job folder or in the historical
   * 'done' job folder.
   */
  def removeJobFolder(jobId: String, isOpenJob: Boolean): Task[Unit]

  /**
   * Persist batch as in "open" state, e.g to be claimed by any node.
   */
  def writeBatchToOpen(processId: ProcessId): Task[Unit]

  /**
   * Remove all directives currently present for the jobId
   */
  def removeAllDirectives(jobId: String): Task[Unit]

  /**
   * Remove passed directives for the given jobId
   */
  def removeDirectives(jobId: String, directives: Set[JobDirective]): Task[Unit]

  /**
   * Write passed directives for the given jobId
   */
  def writeDirectives(jobId: String, directives: Set[JobDirective]): Task[Unit]


}
