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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives.JobDirective
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.OpenJobsSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.JobDefinitionLoadStates.JobDefinitionLoadStatus
import zio.Task


/**
 * Storing new incoming job requests into processable units
 * (job definitions and batchIds to process).
 * Providing a snapshot of the current state of open jobs.
 */
trait JobStateReader {

  /**
   * Logic shall contain all updates of
   * - available jobs sorted by priority and mapped to their definitions
   * - set job level directives
   * - open jobs
   */
  def fetchJobState(isOpenJob: Boolean): Task[OpenJobsSnapshot]

  /**
   * Only find the jobIds of open (unfinished) jobs
   */
  def getOpenJobIds: Task[Set[String]]

  /**
   * Load job definition for a given job directory name (looks it up in the respective open
   * job folder)
   */
  def loadJobDefinitionByJobDirectoryName(jobDirName: String, isOpenJob: Boolean): JobDefinitionLoadStatus

  /**
   * Get job level directives for a given directory name
   */
  def loadJobLevelDirectivesByJobDirectoryName(jobDirName: String, isOpenJob: Boolean): Set[JobDirective]

}
