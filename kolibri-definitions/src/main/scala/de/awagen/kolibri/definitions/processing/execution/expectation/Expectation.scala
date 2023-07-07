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

package de.awagen.kolibri.definitions.processing.execution.expectation

import de.awagen.kolibri.datatypes.io.KolibriSerializable

object Expectation {

  case class SuccessAndErrorCounts(successCount: Int, errorCount: Int)

}

trait Expectation[+T] extends KolibriSerializable {

  def init: Unit

  def succeeded: Boolean

  def statusDesc: String

  override def toString: String = statusDesc

  def accept[TT >: T](element: TT)

  def deepCopy: Expectation[T]

}

