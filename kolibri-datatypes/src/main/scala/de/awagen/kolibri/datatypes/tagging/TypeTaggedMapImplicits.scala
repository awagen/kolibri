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


package de.awagen.kolibri.datatypes.tagging

import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.types.ClassTyped
import org.slf4j.{Logger, LoggerFactory}

import scala.reflect.runtime.universe

object TypeTaggedMapImplicits {

  private[this] val logger: Logger = LoggerFactory.getLogger(TypeTaggedMapImplicits.getClass.toString)

  class TaggedTypeTaggedMap(map: TypeTaggedMap) extends TypeTaggedMap with TaggedWithType {
    override def put[T: universe.TypeTag, V](key: ClassTyped[V], value: T): Option[Any] = map.put(key, value)

    override def remove[T](key: ClassTyped[T]): Option[T] = map.remove(key)

    override def get[V](key: ClassTyped[V]): Option[V] = map.get(key)

    override def keys: Iterable[ClassTyped[Any]] = map.keys

    override def keySet: collection.Set[ClassTyped[Any]] = map.keySet
  }

  implicit class TypeTaggedMapToTaggedTypeTaggedMap(val map: TypeTaggedMap) {

    def toTaggedWithTypeMap: TypeTaggedMap with TaggedWithType = new TaggedTypeTaggedMap(map)

  }

}
