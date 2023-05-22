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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state

import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.actions.JobActions.JobAction
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives.JobDirective
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.rules.Rules.JobDirectiveRules
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates.BatchProcessingStatus


object JobStateSnapshot {

  object TimePlacedOrdering extends Ordering[JobStateSnapshot] {
    def compare(a: JobStateSnapshot, b: JobStateSnapshot): Int = a.timePlacedInMillis compare b.timePlacedInMillis
  }

}


/**
 * Snapshot of current state of a single job.
 */
case class JobStateSnapshot(jobId: String,
                            timePlacedInMillis: Long,
                            jobDefinition: JobDefinition[_, _, _],
                            jobLevelDirectives: Set[JobDirective],
                            batchesToState: Map[Int, BatchProcessingStatus],
                            batchesToProcessingClaimNodes: Map[Int, Set[String]]) {

  /**
   * From given job level directives, derive actions to be taken for the current job.
   * Includes actions such as stopping all processing, only processing it on a few nodes,
   * resume processing,...
   */
  def actionForJob: JobAction = JobDirectiveRules.rule(jobLevelDirectives)


}