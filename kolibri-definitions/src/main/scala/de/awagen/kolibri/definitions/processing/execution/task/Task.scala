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

package de.awagen.kolibri.definitions.processing.execution.task

import de.awagen.kolibri.definitions.processing.execution.task.TaskStates.TaskState
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.types.ClassTyped

import scala.concurrent.ExecutionContext


/**
 * Task representation. Defines the prerequisites that need to be available
 * locally in the passed around TypeTaggedMap,
 * the successKey as key under which result of the execution is stored,
 * fail key under which TaskFailType is stored in case execution fails.
 * start-function provides the actual execution, which returns a TaskState,
 * indicating whether (async) task is still running or done with success/fail.
 * In case of success the TypeTaggedMap has result under successKey, or in case of failure has fail reason under failKey.
 */
trait Task[+T] extends KolibriSerializable {

  def prerequisites: Seq[ClassTyped[Any]]

  def successKey: ClassTyped[T]

  def failKey: ClassTyped[TaskFailType]

  def getFailedPrerequisites(map: TypeTaggedMap): Seq[ClassTyped[_]] = prerequisites.filter(x => !map.keySet.contains(x))

  def start(map: TypeTaggedMap)(implicit ec: ExecutionContext): TaskState

}
