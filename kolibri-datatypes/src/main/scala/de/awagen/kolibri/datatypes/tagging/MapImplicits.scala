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

import scala.collection.mutable

object MapImplicits {

  class TaggedMap[K, V](map: Map[K, V]) extends Map[K, V] with TaggedWithType {
    override def removed(key: K): Map[K, V] = map.removed(key)

    override def updated[V1 >: V](key: K, value: V1): Map[K, V1] = map.updated(key, value)

    override def get(key: K): Option[V] = map.get(key)

    override def iterator: Iterator[(K, V)] = map.iterator
  }

  class MutableTaggedMap[K, V](map: mutable.Map[K, V]) extends mutable.Map[K, V] with TaggedWithType {
    override def subtractOne(elem: K): MutableTaggedMap.this.type = {
      map.subtractOne(elem)
      this
    }

    override def addOne(elem: (K, V)): MutableTaggedMap.this.type = {
      map.addOne(elem)
      this
    }

    override def get(key: K): Option[V] = map.get(key)

    override def iterator: Iterator[(K, V)] = map.iterator
  }

  implicit class MapToTaggedWithTypeMap[K, V](val map: Map[K, V]) {

    def toTaggedWithTypeMap: Map[K, V] with TaggedWithType = new TaggedMap(map)

  }

  implicit class MutableMapToTaggedWithTypeMap[K, V](val map: mutable.Map[K, V]) {

    def toTaggedWithTypeMap: mutable.Map[K, V] with TaggedWithType = new MutableTaggedMap(map)

  }

}
