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

package de.awagen.kolibri.datatypes.mutable.stores

import de.awagen.kolibri.datatypes.types.ClassTyped

import scala.reflect.runtime.universe._


trait TypeTaggedMap {

  def isOfType[T: TypeTag](data: T, typeInstance: Type): Boolean = {
    typeOf[T] <:< typeInstance
  }

  def put[T: TypeTag, V](key: ClassTyped[V], value: T): Option[Any]

  def remove[T](key: ClassTyped[T]): Option[T]

  def get[V](key: ClassTyped[V]): Option[V]

  def keys: Iterable[ClassTyped[Any]]

  def keySet: collection.Set[ClassTyped[Any]]

}
