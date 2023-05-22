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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.OpenJobsSnapshot
import zio.Task


/**
 * Storing new incoming job requests into processable units
 * (job definitions and batchIds to process).
 * Providing a snapshot of the current state of open jobs.
 */
trait JobStateHandler {

  /**
   * Logic shall contain all updates of
   * - available jobs sorted by priority and mapped to their definitions
   * - set job level directives
   * - open jobs
   */
  def fetchOpenJobState: Task[OpenJobsSnapshot]

  // TODO
  // def fetchInProgressState: Task

  /**
   * Move
   */
  def moveToDone(jobDirectoryName: String): Task[Unit]

  /**
   * Storing the job definition content.
   * Storing batch info, and so that each batch can be claimed for processing.
   * After writing job definition and batches, write PROCESS directive into
   * folder to indicate the job is up for processing.
   */
  def storeJobDefinitionAndBatches(jobDefinition: String): Task[Unit]

}
