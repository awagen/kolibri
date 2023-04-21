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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.Jobs.Job

/**
 * Handling global directives regarding the execution of a job.
 * Global directives could include limiting the execution of a job to a specific node or a set of nodes,
 * telling each node to pause the execution and similar.
 * NOTE: every node that processes parts of a job should regularly monitor the directives to react accordingly
 */
trait GlobalDirectiveHandler {

  def fileDirective(job: Job, directive: Directives.Directive): Either[Exception, ()]
  def removeDirective(job: Job, directive: Directives.Directive): Either[Exception, ()]
  def findDirectives(job: Job): Seq[Directives.Directive]

}
