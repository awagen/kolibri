package de.awagen.kolibri.fleet.akka.execution.job

import akka.actor.{ActorContext, ActorSystem}
import akka.stream.Materializer
import akka.stream.scaladsl.RunnableGraph
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.fleet.akka.execution.job.ActorRunnable.JobActorConfig

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
