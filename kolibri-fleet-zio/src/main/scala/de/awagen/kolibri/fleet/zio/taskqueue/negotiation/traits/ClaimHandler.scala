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

import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.ClaimHandler.ClaimTopic.ClaimTopic
import zio._


object ClaimHandler {

  object ClaimTopic extends Enumeration {
    type ClaimTopic = Value

    val JOB_TASK_PROCESSING_CLAIM: Value = Value
    val JOB_TASK_WRAP_UP_CLAIM: Value = Value
    val JOB_TASK_RESET_CLAIM: Value = Value
    val UNKNOWN: Value = Value
  }

}

trait ClaimHandler {


  /**
   * Manage the state of current claims (e.g filing new ones for open jobs
   * if resources available, verifying and exercising claims).
   * This function would usually be the only call that needs to be exposed.
   */
  def manageClaims(claimTopic: ClaimTopic, batchQueueRef: Queue[JobBatch[_, _]]): Task[Unit]

}
