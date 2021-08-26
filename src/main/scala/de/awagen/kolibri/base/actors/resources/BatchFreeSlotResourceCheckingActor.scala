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

package de.awagen.kolibri.base.actors.resources

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import de.awagen.kolibri.base.actors.clusterinfo.ClusterMetricsListenerActor
import de.awagen.kolibri.base.actors.clusterinfo.ClusterMetricsListenerActor.{MetricsProvided, ProvideMetrics}
import de.awagen.kolibri.base.actors.resources.BatchFreeSlotResourceCheckingActor.{AddToRunningBaselineCount, CheckAndUpdateResources}
import de.awagen.kolibri.base.config.AppProperties.config
import de.awagen.kolibri.base.config.AppProperties.config.kolibriDispatcherName

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success


object BatchFreeSlotResourceCheckingActor {

  trait ResourceAdjustingEvent

  case class AddToRunningBaselineCount(value: Int) extends ResourceAdjustingEvent

  case object CheckAndUpdateResources extends ResourceAdjustingEvent

}


class BatchFreeSlotResourceCheckingActor(val resourceChecker: ResourceChecker) extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  // TODO: wed need to collect an average over a certain time to avoid short-time-spike scaling
  implicit val ec: ExecutionContext = context.system.dispatchers.lookup(kolibriDispatcherName)

  val clusterStatusActor: ActorRef = system.actorOf(ClusterMetricsListenerActor.props)

  override def receive: Receive = {
    case CheckAndUpdateResources =>
      val replyTo: ActorRef = sender()
      implicit val timeout: Timeout = Timeout(config.clusterStatusCheckTimeout)
      val statusFuture: Future[Any] = clusterStatusActor ? ProvideMetrics
      statusFuture.onComplete({
        case Success(metricsProvided: MetricsProvided) =>
          val freeSlots: Int = resourceChecker.freeSlots(metricsProvided.stats)
          replyTo ! AddToRunningBaselineCount(freeSlots)
        case _ =>
          log.warning("Could not retrieve cluster status, thus making no adjustment to available resources")
      })
  }
}
