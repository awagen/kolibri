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

import de.awagen.kolibri.datatypes.types.ClassTyped
import org.slf4j.LoggerFactory

import scala.reflect.runtime.universe._

/**
  * NOTE: it is still needed to apply the cast provided, e.g get(key).map(x => key.castFunc.apply(x)) if we want
  * typing to work, e.g all get(key) will yield Option[Any] of the value, but its guaranteed that key.castFunc.apply(x) will work,
  * and only after doing that would special types like Option[Seq[String]] be accepcted by the compiler
  */
case class TypedMapStore(map: scala.collection.immutable.Map[ClassTyped[Any], Any]) extends TypeTaggedMap {

  private val log = LoggerFactory.getLogger(TypedMapStore.getClass)

  override def put[T: TypeTag, V](key: ClassTyped[V], value: T): (Option[T], TypeTaggedMap)  = {
    val typeIsOK = isOfType(value, key.classType)
    if (typeIsOK) (Some(value), TypedMapStore(map + (key -> value)))
    else {
      log.warn(s"value '${value.getClass}' does not match type '${key.classType}' defined by key $key")
      (None, this)
    }
  }

  override def get[V](key: ClassTyped[V]): Option[V] = map.get(key).map(x => key.castFunc.apply(x))

  override def keys: Iterable[ClassTyped[Any]] = map.keys

  override def keySet: collection.Set[ClassTyped[Any]] = map.keys.toSet

  override def remove[T](key: ClassTyped[T]): (Option[T], TypeTaggedMap) = {
    (map.get(key).map(key.castFunc.apply), TypedMapStore(map.filter(x => !x._1.equals(key))))
  }
}
