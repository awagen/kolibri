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

import de.awagen.kolibri.base.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.datatypes.ClassTyped
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future
import scala.reflect.runtime.universe._


case class SimpleAsyncTask[T: TypeTag, U <: T](prerequisites: Seq[ClassTyped[Any]],
                                               successKey: ClassTyped[T],
                                               failKey: ClassTyped[TaskFailType],
                                               futureFunc: TypeTaggedMap => Future[U],
                                               successHandler: (U, TypeTaggedMap) => Unit,
                                               failureHandler: Throwable => Unit)(implicit val tag: TypeTag[U]) extends AsyncTask[T, U] {

  val logger: Logger = LoggerFactory.getLogger(classOf[SimpleAsyncTask[T, _]])
}
