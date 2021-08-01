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

import scala.collection.mutable

case class BaseWeaklyTypedMap(map: mutable.Map[String, Any]) extends WeaklyTypedMap[String] {

  override def put[U](key: String, value: U): Unit = {
    map(key) = value
  }

  override def remove(key: String): Option[Any] = {
    val result = map.get(key)
    if (map.contains(key)) {
      map -= key
    }
    result
  }

  override def get[U](key: String): Option[U] = {
    try {
      map.get(key).map(x => x.asInstanceOf[U])
    }
    catch {
      case _: Exception => None
    }
  }

  override def keys: Iterable[String] = map.keys

  override def keySet: collection.Set[String] = map.keySet
}
