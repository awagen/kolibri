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

import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import zio.{IO, Task}

import java.io.IOException

trait JobHandler {

  /**
   * Give job identifiers of jobs that are already registered by the node to
   * participate in processing
   */
  def registeredJobs: Task[Set[String]]

  /**
   * Scan for new jobs that the node is not yet registered for processing
   */
  def newJobs: Task[Seq[String]]

  /**
   * Scan for new jobs the node is not yet registered for processing
   * and add them with their job definitions to the in-progress registry
   */
  def registerNewJobs: Task[Unit]

  /**
   * Storing the job definition content to the right job folder
   */
  def storeJobDefinition(content: String, jobName: String): IO[IOException, Either[Exception, _]]

  /**
   * Storing empty files into open task folder for the job, one per batch, and
   * each simply named by the batch number so that each batch can be claimed for processing
   */
  def createBatchFilesForJob(jobDefinition: JobDefinition[_]): IO[IOException, Unit]

}
