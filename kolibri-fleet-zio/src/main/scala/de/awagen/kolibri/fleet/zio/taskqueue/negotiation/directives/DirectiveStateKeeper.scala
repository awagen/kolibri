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

trait DirectiveStateKeeper {

  /**
   * Reload the current state of directives
   */
  def reloadExistingDirectives(): Unit

  /**
   * Get directives for a given job
   */
  def getDirectives(job: Job): Seq[Directives.Directive]

  /**
   * Get all jobName - directives assignments as existing in the current state of the state keeper.
   */
  def getAllDirectives: Seq[(String, Seq[Directives.Directive])]

}
