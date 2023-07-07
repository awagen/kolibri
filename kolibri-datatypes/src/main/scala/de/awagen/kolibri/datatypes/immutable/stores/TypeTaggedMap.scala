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

package de.awagen.kolibri.datatypes.immutable.stores

import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.types.ClassTyped

import scala.reflect.runtime.universe._

trait TypeTaggedMap extends KolibriSerializable {

  def isOfType[T: TypeTag](data: T, typeInstance: Type): Boolean = {
    typeOf[T] <:< typeInstance
  }

  def put[T: TypeTag, V](key: ClassTyped[V], value: T): (Option[T], TypeTaggedMap)

  def remove[T](key: ClassTyped[T]): (Option[T], TypeTaggedMap)

  def get[V](key: ClassTyped[V]): Option[V]

  /**
   * In case the key does not directly return a value, try using most generic type (Any) to retrieve the key value
   * (in case of NamedClassTyped only and only if of same name as passed key) and cast to the needed type.
   *
   * NOTE: its a workaround of situations where TypeTag is set to too generic type, e.g in case of generic type format
   * casting to Any type thus not providing specific type information.
   */
  def getWithTypeCastFallback[V](key: ClassTyped[V]): Option[V]

  def keys: Iterable[ClassTyped[Any]]

  def keySet: collection.Set[ClassTyped[Any]]

}
