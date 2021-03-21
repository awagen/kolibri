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

package de.awagen.kolibri.base.actors.routing

import akka.actor.AbstractActor.ActorContext
import akka.actor.{ActorRef, Props}
import akka.cluster.metrics.{AdaptiveLoadBalancingPool, MixMetricsSelector}
import akka.dispatch.Dispatchers
import akka.routing.{Pool, RoundRobinPool}

// TODO: provide programmatic creation of routers here (also group rooters and other strategy, e.g load-balanced)
object RouterUtils {

  def createRoundRobinPoolRouter(routerName: String, nrOfRoutees: Int, routeeProps: Props, actorContext: ActorContext): ActorRef = {
    actorContext.actorOf(new RoundRobinPool(nrOfRoutees).props(routeeProps), routerName)
  }

  def createAdaptiveLoadBalancingPool(nrOfInstances: Int) = {
    AdaptiveLoadBalancingPool(metricsSelector = MixMetricsSelector, nrOfInstances = nrOfInstances,
      supervisorStrategy =  Pool.defaultSupervisorStrategy,
      routerDispatcher = Dispatchers.DefaultDispatcherId,
      usePoolDispatcher = false)
  }



}
