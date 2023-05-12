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


package de.awagen.kolibri.fleet.zio.execution

import de.awagen.kolibri.datatypes.immutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.types.{ClassTyped, NamedClassTyped}
import de.awagen.kolibri.definitions.processing.failure.TaskFailType
import zio.{Task, ZIO}

object ZIOTasks {

  /**
   * Simple task that does nothing except waiting for a given amount of
   * time
   */
  case class SimpleWaitTask(waitTimeInMillis: Long) extends ZIOTask[Unit] {
    override def prerequisites: Seq[ClassTyped[Any]] = Seq.empty

    override def successKey: ClassTyped[Unit] = NamedClassTyped[Unit]("DONE_WAITING")

    override def failKey: ClassTyped[TaskFailType.TaskFailType] = NamedClassTyped[TaskFailType.TaskFailType]("FAILED_WAITING")

    override def task(map: TypeTaggedMap): Task[TypeTaggedMap] = ZIO.attemptBlocking({
      Thread.sleep(waitTimeInMillis)
      map.put(successKey, ())._2
    })
  }
}