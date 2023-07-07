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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.Jobs

/**
 * File-system based implementation of the global directives management.
 * On the top-level folder for the specific job, add/remove directives.
 * We do not need file content for this, correctly named files shall be enough.
 */
case class FileBasedGlobalDirectiveHandler() extends GlobalDirectiveHandler {

  override def fileDirective(job: Jobs.Job, directive: Directives.Directive): Either[Exception, Unit] = ???

  override def removeDirective(job: Jobs.Job, directive: Directives.Directive): Either[Exception, Unit] = ???

  override def findDirectives(job: Jobs.Job): Seq[Directives.Directive] = ???
}
