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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.rules

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.actions.JobActions
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.actions.JobActions.JobAction
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives.JobDirective

object Rules {

  trait RuleSet[A, +B] {

    def rule(precedent: Set[A]): B

  }

  /**
   * Rule set for directives found on job level and mapping to single
   * actions actionable for current node processing.
   * Defines specific action rules even if multiple job-level directives
   * are observed.
   */
  case object JobDirectiveRules extends RuleSet[JobDirective, JobAction] {
    override def rule(precedent: Set[JobDirective]): JobAction = precedent match {
      case e if e.contains(JobDirectives.StopProcessing) => JobActions.StopAllNodes
      case e if e.exists(x => x.isInstanceOf[JobDirectives.OnlyNode]) =>
        val onlyProcessingNodeDirective: JobDirectives.OnlyNode = e.find(x => x.isInstanceOf[JobDirectives.OnlyNode])
          .get.asInstanceOf[JobDirectives.OnlyNode]
        JobActions.ProcessOnlyNode(onlyProcessingNodeDirective.nodeHash)
      case e if e.contains(JobDirectives.Process) => JobActions.ProcessAllNodes
      case _ => JobActions.StopAllNodes
    }
  }

}
