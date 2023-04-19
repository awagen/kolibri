/**
 * Copyright 2022 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.akka.processing

import akka.Done
import akka.actor.ActorRef
import akka.stream.UniqueKillSwitch
import akka.stream.scaladsl.SourceQueueWithComplete
import de.awagen.kolibri.datatypes.AtomicMapPromiseStore
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator

import scala.concurrent.Future

object QueuedRunnableRepository extends AtomicMapPromiseStore[String, (SourceQueueWithComplete[IndexedGenerator[(Any, Option[ActorRef])]], (UniqueKillSwitch, Future[Done]))] {

  override def calculateValue(key: String): (SourceQueueWithComplete[IndexedGenerator[(Any, Option[ActorRef])]], (UniqueKillSwitch, Future[Done])) = {
    throw new IllegalAccessException("need to pass default to retrieve value call, as" +
      "the value always depends on the passed job and can not be determined beforehand")
  }
}
