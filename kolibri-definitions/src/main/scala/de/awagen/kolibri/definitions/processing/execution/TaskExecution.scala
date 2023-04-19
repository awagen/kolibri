/**
 * Copyright 2021 Andreas Wagenmann
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


package de.awagen.kolibri.definitions.processing.execution

import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.execution.task.Task
import de.awagen.kolibri.definitions.processing.execution.task.TaskStates.TaskState
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.types.ClassTyped

import scala.concurrent.ExecutionContext


trait TaskExecution[+T] extends KolibriSerializable {

  def hasFailed: Boolean

  def wasSuccessful: Boolean

  def currentData: TypeTaggedMap with TaggedWithType

  val resultKey: ClassTyped[ProcessingMessage[T]]

  def tasks: Seq[Task[_]]

  def processRemainingTasks(implicit ec: ExecutionContext): TaskState


}
