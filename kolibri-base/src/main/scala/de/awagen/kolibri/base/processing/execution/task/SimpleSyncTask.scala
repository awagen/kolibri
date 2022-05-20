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

import de.awagen.kolibri.base.processing.execution.task.SimpleSyncTask.logger
import de.awagen.kolibri.base.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.types.ClassTyped
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Future, Promise}
import scala.reflect.runtime.universe._


object SimpleSyncTask {

  private val logger: Logger = LoggerFactory.getLogger(SimpleSyncTask.getClass)

}

/**
  * Provides the method to calculate value based on a value map, the key of type ClassTyped[T] to be able to store
  * data in TypeTaggedMap and a Seq of keys that must be in the respective map to execute calculation of the function
  *
  * holds also key for failure (of type ClassTyped[TaskFailType]) and success (of type ClassTyped[T])
  *
  * @param prerequisites
  * @param successKey
  * @param failKey
  * @param func
  * @tparam T
  */
case class SimpleSyncTask[+T: TypeTag](prerequisites: Seq[ClassTyped[Any]],
                                       successKey: ClassTyped[T],
                                       failKey: ClassTyped[TaskFailType],
                                       func: TypeTaggedMap => Either[TaskFailType, T]) extends SyncTask[T] {
  if (isFuture || isPromise) {
    logger.warn(s"func used that computes non-blocking (future/promise); you should not do this within this task definition; if needed" +
      s"rather wrap the execution within an async task type (e.g AsyncTask)")
  }

  def isFuture: Boolean = typeTag[T].tpe <:< typeTag[Future[_]].tpe

  def isPromise: Boolean = typeTag[T].tpe <:< typeTag[Promise[_]].tpe
}
