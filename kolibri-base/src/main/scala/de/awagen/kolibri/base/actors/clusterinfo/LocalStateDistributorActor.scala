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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{Key, ORSet, ORSetKey}
import de.awagen.kolibri.base.actors.work.worker.RunnableExecutionActor.BatchProcessStateResult
import de.awagen.kolibri.base.cluster.ClusterNode


object LocalStateDistributorActor {

  def props: Props = Props[LocalStateDistributorActor]

  val DD_BATCH_INFO_ACTOR_REF_KEY: String = "ddBatchInfoActor"
  val ddBatchStatusActorRefKey: Key[ORSet[ActorRef]] = ORSetKey.create(DD_BATCH_INFO_ACTOR_REF_KEY)


}


case class LocalStateDistributorActor() extends Actor with ActorLogging {


  var batchStatusActor: Option[ActorRef] = None
  // this will lead to Changed messages being received when the value changes
  // note that this actor will also receive a Changed msg in case the distributed data for the specified key is set
  // and the actor subscribes, thus we dont need the Replicator.Get here
  ClusterNode.getSystemSetup.ddReplicator ! Subscribe(LocalStateDistributorActor.ddBatchStatusActorRefKey, self)

  def ddReceive: Receive = {
    case c@Changed(LocalStateDistributorActor.ddBatchStatusActorRefKey) =>
      log.info("successfully retrieved change for batch status actor ref distributed data")
      val value: ActorRef = c.get(LocalStateDistributorActor.ddBatchStatusActorRefKey).elements.toSeq.last
      batchStatusActor = Some(value)
    case g@GetSuccess(LocalStateDistributorActor.ddBatchStatusActorRefKey, _) =>
      log.info("successfully retrieved batch status actor ref distributed data")
      val value: ActorRef = g.get[ORSet[ActorRef]](LocalStateDistributorActor.ddBatchStatusActorRefKey).elements.toSeq.head
      batchStatusActor = Some(value)
    case _@GetFailure(LocalStateDistributorActor.ddBatchStatusActorRefKey, _) =>
      log.warning("failure retrieving batch status actor ref distributed data")
    case NotFound(LocalStateDistributorActor.ddBatchStatusActorRefKey, _) =>
      log.warning("batch status actor ref distributed data not found")
  }


  override def receive: Receive = ddReceive.orElse[Any, Unit](msg => msg match {
    case e: BatchProcessStateResult =>
      if (batchStatusActor.isEmpty) log.info("No batch state actor ref, can not send batch status update")
      batchStatusActor.foreach(x => x.forward(e))
  })
}
