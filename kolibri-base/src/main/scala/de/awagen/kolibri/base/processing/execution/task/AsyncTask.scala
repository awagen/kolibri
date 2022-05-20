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

package de.awagen.kolibri.base.processing.execution.task

import de.awagen.kolibri.base.processing.execution.task.TaskStates.{Done, Running, TaskState}
import de.awagen.kolibri.base.processing.failure.TaskFailType.{FailedByException, MissingPrerequisites}
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.types.ClassTyped

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success}


/**
  * abstract class for AsyncTask. Provides start method to start executing the task and method providing callback
  * to be executed when future is done (either successful or with exception).
  */
abstract class AsyncTask[+T: TypeTag, U <: T](implicit tag: TypeTag[U]) extends Task[T] {

  val resultType: universe.Type = typeOf[T]

  val futureFunc: TypeTaggedMap => Future[U]

  val successHandler: (U, TypeTaggedMap) => Unit

  val failureHandler: Throwable => Unit

  private[this] var future: Option[Future[T]] = None

  /**
    * Call to start execution wrapped within future. In case the task already has a future set, return state indicating
    * that task is already running. Otherwise start computation, place callback on future and return state indicating
    * that execution within future was started
    *
    * @param map
    * @param ec
    * @return
    */
  override def start(map: TypeTaggedMap)(implicit ec: ExecutionContext): TaskState = {
    future match {
      case Some(fut) =>
        Running(fut)
      case None =>
        val failedPrerequisites: Seq[ClassTyped[_]] = getFailedPrerequisites(map)
        if (failedPrerequisites.nonEmpty) {
          Done(Left(MissingPrerequisites(failedPrerequisites)))
        }
        else {
          // andThen call to put value into map to avoid other callback being called with value placement in map
          // not yet completed (would result in failed task processing due to missing result key)
          val fut: Future[U] = futureFunc.apply(map).andThen({
            case Success(value) =>
              map.put(successKey, value)
              successHandler.apply(value, map)
              value
            case Failure(e) =>
              map.put(failKey, FailedByException(e))
              failureHandler.apply(e)
          })
          future = Some(fut)
          Running(fut)
        }
    }
  }

}
