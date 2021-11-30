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

package de.awagen.kolibri.datatypes

import java.util.concurrent.atomic.AtomicReference

object ConcurrentUpdateMapOps {

  def updateMapEntry[U, V](atomicRef: AtomicReference[Map[U, V]], function: Map[U, V] => Map[U, V]): Unit = {
    var currentMap: Map[U, V] = atomicRef.get()
    var isSet: Boolean = false
    while (!isSet) {
      isSet = atomicRef.compareAndSet(currentMap, function.apply(currentMap))
      currentMap = atomicRef.get()
    }
  }

  def updateMapEntryIfKeyNotExists[U, V](atomicRef: AtomicReference[Map[U, V]], key: U, value: => V): Unit = {
    var currentMap: Map[U, V] = atomicRef.get()
    var needsSetting: Boolean = !currentMap.contains(key)
    while (needsSetting) {
      atomicRef.compareAndSet(currentMap, currentMap + (key -> value))
      currentMap = atomicRef.get()
      needsSetting = !currentMap.contains(key)
    }
  }


}
