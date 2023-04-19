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

package de.awagen.kolibri.definitions.domain.jobdefinitions.provider.data

import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues
import de.awagen.kolibri.datatypes.types.ClassTyped

import scala.collection.mutable
import scala.reflect.runtime.universe._

/**
  * extending ClassTyped[T] for type-specific parsing dependent on key.
  * as Enum easily parsable from json, while the actual data can be parsed
  * depending on the key-type, allowing generic parsing of different
  * data providers dependent on key type
  */
object DataKeys extends Enumeration {
  type DataKeys = Val[_]

  case class Val[T: TypeTag](typed: ClassTyped[T]) extends super.Val

  val ORDERED_MULTI_VALUES: Val[OrderedMultiValues] = Val(ClassTyped[OrderedMultiValues])
  val TAGGED_MAP: Val[mutable.Map[String, Seq[Any]]] = Val(ClassTyped[mutable.Map[String, Seq[Any]]])

}
