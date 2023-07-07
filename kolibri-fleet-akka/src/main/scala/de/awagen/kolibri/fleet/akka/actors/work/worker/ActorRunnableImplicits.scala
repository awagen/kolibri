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


package de.awagen.kolibri.fleet.akka.actors.work.worker

import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.fleet.akka.execution.job.{ActorRunnable, ActorType, QueuedActorRunnable}
import de.awagen.kolibri.fleet.akka.execution.job.ActorRunnable.JobActorConfig


object ActorRunnableImplicits {

  implicit class QueuedRunnableConvertible[U, V, V1, Y <: WithCount](runnable: ActorRunnable[U, V, V1, Y]){

    def toQueuedRunnable(config: JobActorConfig): QueuedActorRunnable[U, V, V1, Y] = {
      QueuedActorRunnable(
        runnable.jobId,
        runnable.batchNr,
        runnable.supplier.mapGen(x => (x, config.others.get(ActorType.ACTOR_SINK))),
        runnable.transformer,
        runnable.processingActorProps,
        runnable.aggregatorConfig,
        runnable.expectationGenerator,
        runnable.sinkType,
        runnable.waitTimePerElement,
        runnable.maxExecutionDuration,
        runnable.sendResultsBack)
    }
  }

}
