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
