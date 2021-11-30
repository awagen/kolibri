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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.routing.FromConfig
import de.awagen.kolibri.base.actors.work.manager.WorkManagerActor
import de.awagen.kolibri.base.processing.execution.job.ActorRunnable

object RoutingActor {

  def props: Props = Props[RoutingActor]

  def props(routeeProps: Props): Props = Props(new RoutingActor(routeeProps))

  def defaultProps: Props = RoutingActor.props(Props[WorkManagerActor])

}


case class RoutingActor(routeeProps: Props = Props[WorkManagerActor]) extends Actor with ActorLogging {

  //configured pool router (see application.conf)
  //see also https://doc.akka.io/docs/akka/2.5/cluster-routing.html
  val poolRouter: ActorRef = context.actorOf(FromConfig.props(routeeProps), name = "poolRouter")

  override def receive: Receive = {
    case job: ActorRunnable[_, _,_,_] =>
      log.info("routingActor: received routed batch for processing")
      poolRouter forward job
    case msg: Any =>
      log.warning(s"received unknown message (forwarding to routees anyways): $msg")
      poolRouter forward msg
  }

}
