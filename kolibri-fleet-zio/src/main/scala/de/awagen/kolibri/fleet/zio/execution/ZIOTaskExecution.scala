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


package de.awagen.kolibri.fleet.zio.execution

import de.awagen.kolibri.datatypes.immutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.types.ClassTyped
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.TaskFailType

sealed trait ExecutionState

sealed case class Failed(taskIndex: Int, taskFailType: TaskFailType) extends ExecutionState

object Success extends ExecutionState


trait ZIOTaskExecution[+T] extends KolibriSerializable {

  def hasFailed(executionStates: Seq[ExecutionState]): Boolean

  def wasSuccessful(executionStates: Seq[ExecutionState]): Boolean

  def initData: TypeTaggedMap

  val resultKey: ClassTyped[T]

  def tasks: Seq[ZIOTask[_]]

  /**
   * Execute full sequence of tasks till either all succeeded or
   * some task failed
   */
  def processAllTasks: zio.Task[(TypeTaggedMap, Seq[ExecutionState])]


}
