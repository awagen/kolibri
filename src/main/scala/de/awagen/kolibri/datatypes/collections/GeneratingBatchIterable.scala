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

import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable

/**
  * Implementatin that on each next call only provides the IndexedGenerator of the next batch, meaning this iterable
  * will itself create elements only on demand when elements are requested, since IndexedGenerator assumes a mechanism
  * to calculate the i-th element instead of holding all elements in memory
  *
  * @param baseIterable
  * @param batchSize
  * @tparam T
  */
case class GeneratingBatchIterable[+T](baseIterable: IndexedGenerator[T],
                                       batchSize: Int) extends BatchIterable[IndexedGenerator[T]] with KolibriSerializable {

  override def iterator: Iterator[IndexedGenerator[T]] = {
    new Iterator[IndexedGenerator[T]] {
      val nrOfElements: Int = (baseIterable.nrOfElements / batchSize) + (if (baseIterable.nrOfElements % batchSize == 0) 0 else 1)
      var currentBatchNr: Int = 0

      def elementsUsedSoFar: Int = batchSize * currentBatchNr

      override def hasNext: Boolean = currentBatchNr < nrOfElements

      override def next(): IndexedGenerator[T] = {
        val startIndex: Int = elementsUsedSoFar
        val endIndex: Int = elementsUsedSoFar + batchSize
        currentBatchNr += 1
        baseIterable.getPart(startIndex, endIndex)
      }
    }
  }
}
