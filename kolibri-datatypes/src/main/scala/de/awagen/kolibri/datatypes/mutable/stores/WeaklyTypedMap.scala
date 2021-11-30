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

import de.awagen.kolibri.datatypes.io.KolibriSerializable

trait WeaklyTypedMap[T] extends KolibriSerializable {

  def put[U](key: T, value: U): Unit

  def remove(key: String): Option[Any]

  def get[U](key: T): Option[U]

  def keys: Iterable[T]

  def keySet: collection.Set[T]

}
