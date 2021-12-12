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
import akka.cluster.ddata.{Key, ORSet}
import de.awagen.kolibri.base.actors.work.worker.RunnableExecutionActor.BatchProcessStateResult
import de.awagen.kolibri.base.cluster.ClusterNode


object LocalStateDistributorActor {

  def props: Props = Props[LocalStateDistributorActor]

}


case class LocalStateDistributorActor() extends Actor with ActorLogging {

  var batchStatusActor: Option[ActorRef] = None

  val ddBatchStatusActorRefKey: Key[ORSet[ActorRef]] = DDResourceStateUtils.DD_BATCH_STATUS_ACTOR_REF_KEY
  // this will lead to Changed messages being received when the value changes
  // note that this actor will also receive a Changed msg in case the distributed data for the specified key is set
  // and the actor subscribes, thus we dont need the Replicator.Get here
  ClusterNode.getSystemSetup.ddReplicator ! Subscribe(ddBatchStatusActorRefKey, self)

  def ddReceive: Receive = DistributedDataActorHelper.stateChangeReceive[ORSet[ActorRef]](
    ddBatchStatusActorRefKey,
    "batch status actor ref",
    valueHandleFunc)

  val valueHandleFunc: ORSet[ActorRef] => Unit = set => {
    val value: ActorRef = set.elements.toSeq.last
    batchStatusActor = Some(value)
  }


  override def receive: Receive = ddReceive.orElse[Any, Unit](msg => msg match {
    case e: BatchProcessStateResult =>
      if (batchStatusActor.isEmpty) log.info("No batch state actor ref, can not send batch status update")
      batchStatusActor.foreach(x => x.forward(e))
  })
}
