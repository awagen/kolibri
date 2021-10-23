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


package de.awagen.kolibri.base.routing

import akka.actor.{ActorContext, ActorRef}
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.routing.RoundRobinPool
import de.awagen.kolibri.base.actors.work.manager.WorkManagerActor

object Routers {

  // NOTE: do not require the routees to have any props arguments.
  // this can lead to failed serialization on routee creation
  // (even if its just a case class with primitives, maybe due to disabled
  // java serialization and respective msg not being of KolibriSerializable.
  // Not fully clear yet though
  def createWorkerRoutingServiceForJob(jobId: String)(implicit context: ActorContext): ActorRef = {
    context.actorOf(
      ClusterRouterPool(
        RoundRobinPool(0),
        // right now we want to have a single WorkManager per node. Just distributes the batches
        // to workers on its respective node and keeps state of running workers
        ClusterRouterPoolSettings(
          totalInstances = 10,
          maxInstancesPerNode = 2,
          allowLocalRoutees = true,
          useRoles = Set("compute")))
        .props(WorkManagerActor.props),
      name = s"jobBatchRouter-$jobId")
  }

}
