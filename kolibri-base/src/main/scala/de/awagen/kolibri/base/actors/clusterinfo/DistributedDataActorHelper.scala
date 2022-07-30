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


package de.awagen.kolibri.base.actors.clusterinfo

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import akka.cluster.ddata.{Key, ORSet, ReplicatedData}
import akka.cluster.ddata.Replicator.{Changed, GetFailure, GetSuccess, NotFound}
import org.slf4j.{Logger, LoggerFactory}

object DistributedDataActorHelper {

  private[this] val log: Logger = LoggerFactory.getLogger(DistributedDataActorHelper.getClass)

  def stateChangeReceive[T <: ReplicatedData](filterKey: Key[T], valueDesc: String, valueHandleFunc: T => Unit): Receive = {
    case c@Changed(key: Key[T]) if key == filterKey =>
      log.info(s"changed: successfully retrieved distributed data change for: $valueDesc - new value: ${c.get(key)}")
      valueHandleFunc.apply(c.get(key))
    case g@GetSuccess(key: Key[T], _) if key == filterKey =>
      log.info(s"getSuccess: successfully retrieved distributed data for: $valueDesc - value: ${g.get(key)}")
      valueHandleFunc.apply(g.get(key))
    case _@GetFailure(key: Key[T], _) if key == filterKey =>
      log.warn(s"failure: failed retrieving distributed data for: $valueDesc")
    case NotFound(key: Key[ORSet[ActorRef]], _) if key == filterKey =>
      log.warn(s"key '$key' not found")
  }

  // resulting receive when combining multiple for different keys
  def multipleStateChangeReceive[T <: ReplicatedData](filterKeysAndDescriptionsAndValueHandleFunc: Seq[(Key[T], String, T => Unit)]): Receive = {
    val receives: Seq[Receive] = filterKeysAndDescriptionsAndValueHandleFunc.map(keyAndDescAndHandleFunc => {
      stateChangeReceive(keyAndDescAndHandleFunc._1, keyAndDescAndHandleFunc._2, keyAndDescAndHandleFunc._3)
    })
    var doNothingReceive: PartialFunction[Any, Unit] =  {
      case _ => ()
    }
    if (receives.nonEmpty) doNothingReceive = receives.head
    receives.slice(1, receives.size).foldLeft(doNothingReceive)((x, y) => x.andThen[Unit](y))
  }

}
