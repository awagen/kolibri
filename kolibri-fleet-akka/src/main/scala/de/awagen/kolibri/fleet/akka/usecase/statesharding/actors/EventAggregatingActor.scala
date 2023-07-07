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


package de.awagen.kolibri.fleet.akka.usecase.statesharding.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import org.slf4j.{Logger, LoggerFactory}

object EventAggregatingActor {

  val logger: Logger = LoggerFactory.getLogger(EventAggregatingActor.getClass)

  case class EventCountStore[U]() {
    private[this] var map: Map[U, Int] = Map.empty

    def accept(element: U): Unit = {
      map = map + (element -> (map.getOrElse(element, 0) + 1))
    }

    override def toString: String = map.toString()
  }

  sealed trait RequestEvent extends KolibriSerializable {
    def requestId: String
    def sourceId: String
  }

  case class EntityEvent(sourceId: String, requestId: String, event: String, targetEntityId: String) extends RequestEvent

  case class KeyValueEvent(sourceId: String, requestId: String, event: String, key: String, value: String) extends RequestEvent

  case class CombinedEvent(sourceId: String, requestId: String, entityEvent: EntityEvent, keyValueEvent: KeyValueEvent) extends RequestEvent

  def getEntityEventStore: EventCountStore[EntityEvent] = EventCountStore()

  def getKeyValueEventStore: EventCountStore[KeyValueEvent] = EventCountStore()

  object RequestEventStore {

    def empty: RequestEventStore = RequestEventStore(
      EventCountStore[EntityEvent](),
      EventCountStore[KeyValueEvent]()
    )

  }

  case class RequestEventStore(entityEventStore: EventCountStore[EntityEvent],
                               keyValueEventStore: EventCountStore[KeyValueEvent])

  def acceptEvent(storeSequence: Boolean = false,
                  maxEvents: Int = 10)
                 (event: RequestEvent,
                  requestEventStore: RequestEventStore,
                  eventSequence: Seq[RequestEvent] = Seq.empty): (RequestEventStore, Seq[RequestEvent]) = {
    logger.info(s"received event: $event")
    event match {
      case e: EntityEvent =>
        requestEventStore.entityEventStore.accept(e)
      case e: KeyValueEvent =>
        requestEventStore.keyValueEventStore.accept(e)
      case e: CombinedEvent =>
        requestEventStore.entityEventStore.accept(e.entityEvent)
        requestEventStore.keyValueEventStore.accept(e.keyValueEvent)
    }
    val newSequence: Seq[RequestEvent] = (if (storeSequence) eventSequence :+ event else eventSequence)
      .takeRight(maxEvents)
    logger.info(s"resulting event store: (${requestEventStore.entityEventStore}, ${requestEventStore.keyValueEventStore})")
    logger.info(s"resulting event seq: $newSequence")
    (requestEventStore,newSequence )
  }

  // the below setup can be simplified to Behaviors.receive {(context, message) => ... in case no initial setup
  // such as watching another actor or similar is needed
  def apply(requestEventStore: RequestEventStore,
            storeSequence: Boolean = false,
            eventSequence: Seq[RequestEvent] = Seq.empty,
            maxEvents: Int = 10): Behavior[RequestEvent] = Behaviors.setup { _ =>
    Behaviors
      .receivePartial[RequestEvent] {
        case (_, event: RequestEvent) =>
          val data: (RequestEventStore, Seq[RequestEvent]) = acceptEvent(storeSequence, maxEvents)(event, requestEventStore, eventSequence)
          apply(data._1, storeSequence, data._2, maxEvents)
      }
      .receiveSignal {
        case (_, akka.actor.typed.Terminated(_)) =>
          Behaviors.stopped
      }
  }

  def apply(): Behavior[RequestEvent] = apply(
    RequestEventStore(getEntityEventStore, getKeyValueEventStore)
  )

}
