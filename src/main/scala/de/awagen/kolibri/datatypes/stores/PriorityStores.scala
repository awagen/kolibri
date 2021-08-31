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


package de.awagen.kolibri.datatypes.stores

import scala.collection.mutable

object PriorityStores {

  abstract class PriorityStore[T, U] {

    private[this] val reversedPerParamMap: mutable.Map[T, mutable.PriorityQueue[U]] = mutable.Map.empty

    def keep_n: Int

    def ordering: Ordering[U]

    def elementToKey: U => T

    def queueReversed: mutable.PriorityQueue[U] = new mutable.PriorityQueue[U]()(ordering).reverse

    def addEntry(entry: U): Unit = {
      val key = elementToKey.apply(entry)
      if (!reversedPerParamMap.keySet.contains(key)) {
        reversedPerParamMap(key) = queueReversed
      }
      val reversedQueue = reversedPerParamMap(key)
      reversedQueue.enqueue(entry)
      while (reversedQueue.size > keep_n) reversedQueue.dequeue()
    }

    def result: Map[T, Seq[U]] = reversedPerParamMap.view.mapValues(
      queue => queue.toSeq.sorted(ordering).reverse
    ).toMap

  }

  case class BasePriorityStore[T, U](keep_n: Int,
                                     ordering: Ordering[U],
                                     elementToKey: U => T) extends PriorityStore[T, U]

}
