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

import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * Implementation ensuring thread safety of the value storage and also ensuring
  * that no more than one request leads to the creation of the stored resource which
  * could potentially be expensive (e.g in case multiple experiment batch processing
  * actors on a single node try to request the ressource at once)
  * E.g used to load some data expensive to load within an object to have only one data-instance per node
 *
  * @tparam U - type of the key used to identify a value
  * @tparam V - type of the corresponding value
  */
trait AtomicMapPromiseStore[U,V]  {

  private[this] val logger: Logger = LoggerFactory.getLogger("AtomicMapPromiseStore")

  private[this] val valueMap: AtomicReference[Map[U, Promise[V]]] = new AtomicReference(Map.empty)

  /**
   *
   * @param key - key for which the data is requested
   * @param default - if set, this value will be taken instead of the one that would be generated from calling
   *                calculateValue on the key value
   * @param ec - the execution context
   * @return the promise for the value requested for key. If none is set yet for the key, will utilize default supplier
   *         if defined, otherwise call calculateValue on the requested key value to determine the requested value.
   *         Also stores the determined value in the map (if not yet set)
   */
  private[this] def loadAndStoreValues(key: U, default: Option[SerializableSupplier[V]] = None)(implicit ec: ExecutionContext): Promise[V] = {
    var currentMap: Map[U, Promise[V]] = valueMap.get()
    var didSetPromise: Boolean = false
    while (!currentMap.keySet.contains(key)){
      didSetPromise = valueMap.compareAndSet(currentMap, currentMap + (key -> Promise[V]()))
      if (didSetPromise) valueMap.get()(key).completeWith(Future{default.map(x => x.apply()).getOrElse(calculateValue(key))})
      currentMap = valueMap.get()
    }
    valueMap.get()(key)
  }

  /**
   * Retrieval function for value for a given key.
   * @param key - key value
   * @param default - supplier for value in case none is yet set for the key
   * @param ec - execution context
   * @return requested value for key
   */
  def retrieveValue(key: U, default: Option[SerializableSupplier[V]] = None)(implicit ec: ExecutionContext): Promise[V] = {
    if (!valueMap.get().contains(key)) loadAndStoreValues(key, default)
    else valueMap.get()(key)
  }

  /**
   * Remove the data corresponding to the passed key.
   * @param key
   */
  def remove(key: U): Unit = {
    var removedMapEntry: Boolean = false
    var currentMap = valueMap.get()
    while (currentMap.contains(key) && !removedMapEntry){
      removedMapEntry = valueMap.compareAndSet(currentMap, currentMap - key)
      currentMap = valueMap.get()
    }
    logger.info(s"removed key '$key', remaining keys: '${valueMap.get().keySet}'")
  }

  /**
   * Clears all values set so far
   */
  def clearAll(): Unit = {
    var currentMap: Map[U, Promise[V]] = valueMap.get()
    var didRemoveAll: Boolean = false
    while (currentMap.nonEmpty && !didRemoveAll){
      didRemoveAll = valueMap.compareAndSet(currentMap, Map.empty)
      currentMap = valueMap.get()
    }

  }

  /**
   * True if the mapping already contains the passed key value
   * @param key
   * @return
   */
  def contains(key: U): Boolean = {
    valueMap.get().contains(key)
  }

  /**
   * Function to generate value for a passed key. Note that ensuring thread-safety and only single executions
   * is ensured in the above methods, so on extension only need to override this function with the plain
   * generation of the value (e.g file / db access or the like)
   * @param key key value
   * @return generated value
   */
  def calculateValue(key: U): V
}
