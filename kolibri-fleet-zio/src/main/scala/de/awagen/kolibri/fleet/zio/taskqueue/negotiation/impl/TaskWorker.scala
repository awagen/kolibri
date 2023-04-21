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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.impl

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.WorkStatus.WorkStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.Worker
import zio.Task

case class TaskWorker() extends Worker {
  /**
   * The computation of a task
   *
   * @return
   */
  override def work[T](): Task[T] = ???

  /**
   * Returns current work status for the given task
   *
   * @return
   */
  override def status(): Task[WorkStatus] = ???
}