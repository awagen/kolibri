/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio.resources

import de.awagen.kolibri.datatypes.AtomicMapPromiseStore
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import zio.{Task, ZIO}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Promise}

case class ZIOAtomicMapPromiseStore[U, V, W](atomicStore: AtomicMapPromiseStore[U, V, W],
                                             awaitTimeOutInSeconds: Int) {

  /**
   * Task that will return None if key is not set in atomicStore,
   * and if set will wait for the respective promise for defined amount of time,
   * otherwise fail with timeout exception
   */
  def get(key: U): Task[Option[V]] = ZIO.attemptBlockingIO(atomicStore.get(key)
    .map(p => Await.result(p.future, FiniteDuration(awaitTimeOutInSeconds, TimeUnit.SECONDS)))
  )

  /**
   * If key is not set, will fail with IllegalArgumentException.
   * If set, will wait for defined amount of time and return the value if available,
   * and fail with TimeoutException if exceeding time
   * @param key
   * @return
   */
  def getAwaited(key: U): Task[V] = getPromise(key)
    .map(p => Await.result(p.future, FiniteDuration(awaitTimeOutInSeconds, TimeUnit.SECONDS)))

  /**
   * Fails with IllegalArgumentException if key not in atomicStore.
   * If key is set, returns corresponding promise without waiting for any
   * fulfilling of the promise
   */
  def getPromise(key: U): Task[Promise[V]] = ZIO.fromOption(atomicStore.get(key))
    .mapError(_ => new IllegalArgumentException(s"Key '$key' not available"))

  /**
   * Function to be used on initial calculation of the value for a key.
   * Sets in motion the calculation of the data and makes sure for each key calculation
   * only happens once.
   * Type differs here from the actual key in the underlying map implementation
   * to avoid having to pass all info used for generation of data
   * when requesting it
   */
  def retrieveValue(retrieveObj: W, default: Option[SerializableSupplier[V]] = None): Task[V] = {
    ZIO.blocking(
      ZIO.fromFuture { implicit ec: ExecutionContext => atomicStore.retrieveValue(retrieveObj, default).future }
    )
  }

  /**
   * Remove data for given key from the underlying atomic store
   */
  def remove(key: U): Task[Unit] = ZIO.attempt(atomicStore.remove(key))

  /**
   * Removing all key-value pairs from the underlying atomic store
   */
  def clearAll(): Task[Unit] = ZIO.attempt(atomicStore.clearAll())

  /**
   * Checks if a given key exists in the underlying atomic store
   */
  def contains(key: U): Task[Boolean] = ZIO.attempt(atomicStore.contains(key))

}
