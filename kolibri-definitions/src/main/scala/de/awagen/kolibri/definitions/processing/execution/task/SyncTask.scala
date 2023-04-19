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

import de.awagen.kolibri.definitions.processing.execution.task.TaskStates.{Done, TaskState}
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.{MissingPrerequisites, TaskFailType}
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._


/**
  * Note on TypeTags: storing a fixed value of typeTag as attribute on a covariant type parameter does not work.
  * Otherwise typeTag[T] is a way of retrieving the type tag for type T, or typeOf providing the Type.
  * Note also that in multithreaded execution, using type tags might be slow due to needed synchronization.
  * This we will likely be able to avoid in the application setup due to Akka usage.
  *
  * @tparam T
  */
abstract class SyncTask[+T: TypeTag] extends Task[T] {

  val resultType: universe.Type = typeOf[T]

  def func: TypeTaggedMap => Either[TaskFailType, T]

  def execute(data: TypeTaggedMap): Either[TaskFailType, T] = func.apply(data)

  override def start(map: TypeTaggedMap)(implicit ec: ExecutionContext): TaskState = {
    val failedPrereqs = getFailedPrerequisites(map)
    if (failedPrereqs.nonEmpty) Done(Left(MissingPrerequisites(failedPrereqs)))
    else {
      Done(executeAndAddToMapAndReturn(map))
    }
  }

  /**
    * we have to have this method here cause this is the place where we still have the TypeTag[T] ... otherwise
    * the key (successKey of type ClassTyped[T]) to value comparison will fail e.g if task is executed in a generic Seq[Task[_]], causing strange behavior,
    * so keep it consistent here or at least change the map's put method such that the task refers to itself for a check
    * whether key and value fit to each other, that is probably enough, too
    *
    * @param data
    * @return
    */
  private[this] def executeAndAddToMapAndReturn(data: TypeTaggedMap): Either[TaskFailType, T] = {
    val result: Either[TaskFailType, T] = func.apply(data)
    result match {
      case Left(failType) => data.put(failKey, failType)
      case Right(value) => data.put(successKey, value)
    }
    result
  }

}
