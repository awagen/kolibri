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

import scala.concurrent.ExecutionContext


case class ZIOAtomicMapPromiseStore[U, V, W](atomicStore: AtomicMapPromiseStore[U, V, W]) extends {

  def retrieveValue(retrieveObj: W, default: Option[SerializableSupplier[V]] = None): Task[V] = {
    ZIO.blocking(
      ZIO.fromFuture { implicit ec: ExecutionContext => atomicStore.retrieveValue(retrieveObj, default).future }
    )
  }

  def remove(key: U): Task[Unit] = ZIO.attempt(atomicStore.remove(key))

  def clearAll(): Task[Unit] = ZIO.attempt(atomicStore.clearAll())

  def contains(key: U): Task[Boolean] = ZIO.attempt(atomicStore.contains(key))

}
