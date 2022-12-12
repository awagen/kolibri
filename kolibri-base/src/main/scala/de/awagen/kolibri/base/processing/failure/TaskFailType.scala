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

package de.awagen.kolibri.base.processing.failure

import de.awagen.kolibri.datatypes.types.ClassTyped

import scala.reflect.runtime.universe._

object TaskFailType {

  trait TaskFailType

  case class MultiTaskFailType(failTypes: Seq[TaskFailType]) extends TaskFailType

  case object NotExistingTask extends TaskFailType

  case object NotReadyToStartTaskExecution extends TaskFailType

  case object NotYetStartedTask extends TaskFailType

  case object FailedWrite extends TaskFailType

  sealed case class FailedByException(e: Throwable) extends TaskFailType {
    override def toString: String = {
      s"FailedByException(${e.getClass.getName})"
    }
  }

  sealed case class MissingPrerequisites(failedKeys: Seq[ClassTyped[_]]) extends TaskFailType

  sealed case class MissingResultKey[T](key: ClassTyped[T]) extends TaskFailType

  sealed case class FailByMapTypeCheck(expectedType: Type, actualType: Type) extends TaskFailType

  case object EmptyMetrics extends TaskFailType

  case class UnknownResponseClass(className: String) extends TaskFailType

}