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

package de.awagen.kolibri.base.processing.execution.job

import akka.actor.{Actor, ActorLogging, ActorSystem}
import de.awagen.kolibri.base.actors.work.worker.AggregatingActor
import de.awagen.kolibri.base.config.AppProperties
import de.awagen.kolibri.base.processing.execution.job.ActorRunnable.JobActorConfig

import scala.collection.immutable
import scala.concurrent.ExecutionContextExecutor

object TestActorRunnableActor {

  case class IntMsg(value: Int)

}

class TestActorRunnableActor() extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val readyForJob: Receive = {
    case e: ActorRunnable[_, _, _,_] =>
      val actorConfig = JobActorConfig(self, immutable.Map(ActorType.ACTOR_SINK -> sender()))
      val runnableGraph = e.getRunnableGraph(actorConfig)
      runnableGraph.run()
    case _ if AppProperties.config.useAggregatorBackpressure =>
      sender() ! AggregatingActor.ACK
  }

  override def receive: Receive = readyForJob

}
