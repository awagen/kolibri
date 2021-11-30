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

import de.awagen.kolibri.datatypes.io.KolibriSerializable

/**
  * Base implementation on each next call requesting batchSize next elements from the iterator corresponding to initially
  * passed iterable.
  *
  * @param baseIterable
  * @param batchSize
  * @tparam T
  */
case class BaseBatchIterable[+T](baseIterable: Iterable[T],
                                 batchSize: Int) extends BatchIterable[Iterable[T]] with KolibriSerializable {

  override def iterator: Iterator[Iterable[T]] = {
    val iter: Iterator[T] = baseIterable.iterator

    new Iterator[Iterable[T]] {
      override def hasNext: Boolean = iter.hasNext

      override def next(): Iterable[T] = {
        var elements = Seq.empty[T]
        while (iter.hasNext && elements.size < batchSize) {
          elements = elements :+ iter.next()
        }
        elements
      }
    }
  }
}
