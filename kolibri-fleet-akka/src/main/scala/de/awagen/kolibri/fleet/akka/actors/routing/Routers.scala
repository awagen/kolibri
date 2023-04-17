package de.awagen.kolibri.fleet.akka.actors.routing

import akka.actor.{ActorContext, ActorRef}
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.routing.RoundRobinPool
import de.awagen.kolibri.fleet.akka.actors.work.manager.WorkManagerActor

object Routers {

  val COMPUTE_ROLE = "compute"

  // NOTE: do not require the routees to have any props arguments.
  // this can lead to failed serialization on routee creation
  // (even if its just a case class with primitives, maybe due to disabled
  // java serialization and respective msg not being of KolibriSerializable.
  // Not fully clear yet though
  def createWorkerRoutingServiceForJob(jobId: String)(implicit context: ActorContext): ActorRef = {
    context.actorOf(
      ClusterRouterPool(
        RoundRobinPool(0),
        // WorkManager routee just distributes the batches to workers on its respective node
        // and keeps state of running workers
        ClusterRouterPoolSettings(
          totalInstances = 10,
          maxInstancesPerNode = 2,
          allowLocalRoutees = true,
          useRoles = Set(COMPUTE_ROLE)))
        .props(WorkManagerActor.props),
      name = s"jobBatchRouter-$jobId")
  }

}
