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

package de.awagen.kolibri.datatypes.collections

case class CombinedIterator[U, V, W](iterable1: Iterable[U], iterable2: Iterable[V],
                                     mergeFunc: (U, V) => W) extends Iterator[Iterator[W]] {

  private[this] val iterator1: Iterator[U] = iterable1.iterator

  def hasNext: Boolean = iterator1.hasNext

  def next(): Iterator[W] = {
    val nextElement1 = iterator1.next()
    new Iterator[W] {
      val iterator2: Iterator[V] = iterable2.iterator

      override def hasNext: Boolean = iterator2.hasNext

      override def next(): W = {
        mergeFunc(nextElement1, iterator2.next())
      }
    }
  }
}
