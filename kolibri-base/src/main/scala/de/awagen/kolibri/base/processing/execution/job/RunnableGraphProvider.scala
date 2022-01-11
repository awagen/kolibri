package de.awagen.kolibri.base.processing.execution.job

import akka.actor.{ActorContext, ActorSystem}
import akka.stream.Materializer
import akka.stream.scaladsl.RunnableGraph
import de.awagen.kolibri.base.processing.execution.job.ActorRunnable.JobActorConfig
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

trait RunnableGraphProvider[+U, +V1] {

  def jobId: String

  def supplier: IndexedGenerator[U]

  def waitTimePerElement: FiniteDuration

  def maxExecutionDuration: FiniteDuration

  def sendResultsBack: Boolean

  def getRunnableGraph(actorConfig: JobActorConfig)(implicit actorSystem: ActorSystem, actorContext: ActorContext, mat: Materializer, ec: ExecutionContext): RunnableGraph[V1]

}
