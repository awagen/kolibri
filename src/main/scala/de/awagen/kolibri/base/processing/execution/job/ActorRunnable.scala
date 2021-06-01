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

import akka.actor.{ActorContext, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.util.Timeout
import akka.{Done, NotUsed}
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.BadCorn
import de.awagen.kolibri.base.config.AppConfig.config
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.base.processing.execution.job
import de.awagen.kolibri.base.processing.execution.job.ActorRunnable._
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType._
import de.awagen.kolibri.base.processing.failure.TaskFailType
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.DataStore
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableSupplier}
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object ActorRunnable {

  case class JobActorConfig(executing: ActorRef, others: immutable.Map[ActorType.Value, ActorRef])

  case class JobBatchElement[V](jobId: String, batchNr: Int, element: V)

}

/**
  * Allows definition of workflow. The runnable graph will only utilize the actorSink
  * if the JobActorConfig passed to the generator actually contains actor type REPORT_TO,
  * otherwise the graph utilitzes Sink.ignore
  *
  * @tparam U - input element type
  * @tparam V - message type to be send to actor passed to actorSink function per element
  * @param jobId                - id of the runnable
  * @param batchNr              - nr of the batch of the overall job
  * @param supplier             - the IndexedGenerator[U] of data to process
  * @param transformer          - the transformation applied to elements of the supplier, U => V
  * @param processingActorProps - if set, send ProcessingMessage[V] to actor of specified type for processing
  * @param expectationGenerator - the execution expectation. Can be utilized by actor running the actorRunnable to determine whether
  *                             processing succeeded, failed due to processing or due to exceeding timeout
  * @param aggregationSupplier  - Supplier for aggregator. Typed by type to expect for aggregation (wrapped within ProcessingMessage) and type of aggregation
  * @param returnType           - only if set not set to IGNORE_SINK and actorConfig passed to getRunnableGraph contains REPORT_TO actor type
  *                             will the result of each transformation (U => V) of type V be send to the actorRef given by REPORT_TO type in the actorConfig.
  *                             Otherwise Sink.ignore is used. Note that the expectation can still be non empty in case the sending of responses
  *                             does not happen in the sink here but messages are send to other processors which then provide the response
  * @param waitTimePerElement   - max time to wait per processing element (in case processingActorProps are not None; only used to set timeout to the respective ask future)
  */
case class ActorRunnable[U, V, V1, Y](jobId: String,
                                      batchNr: Int,
                                      supplier: IndexedGenerator[U],
                                      transformer: Flow[U, TaggedWithType[Tag] with DataStore[V], NotUsed],
                                      processingActorProps: Option[Props],
                                      expectationGenerator: SerializableFunction1[Int, ExecutionExpectation],
                                      aggregationSupplier: SerializableSupplier[Aggregator[TaggedWithType[Tag] with DataStore[V1], Y]],
                                      returnType: job.ActorRunnableSinkType.Value,
                                      waitTimePerElement: FiniteDuration,
                                      maxExecutionDuration: FiniteDuration) extends KolibriSerializable {

  val log: Logger = LoggerFactory.getLogger(ActorRunnable.getClass)

  val actorSink: SerializableFunction1[ActorRef, Sink[TaggedWithType[Tag] with DataStore[V1], Future[Done]]] = new SerializableFunction1[ActorRef, Sink[TaggedWithType[Tag] with DataStore[V1], Future[Done]]] {
    override def apply(actorRef: ActorRef): Sink[TaggedWithType[Tag] with DataStore[V1], Future[Done]] = Sink.foreach[TaggedWithType[Tag] with DataStore[V1]](element => {
      actorRef ! element
      ()
    })
  }

  def getSink(actorConfig: JobActorConfig): Sink[TaggedWithType[Tag] with DataStore[V1], (UniqueKillSwitch, Future[Done])] = {
    val killSwitch: Flow[TaggedWithType[Tag] with DataStore[V1], TaggedWithType[Tag] with DataStore[V1], UniqueKillSwitch] = Flow.fromGraph(KillSwitches.single[TaggedWithType[Tag] with DataStore[V1]])
    returnType match {
      case IGNORE_SINK =>
        killSwitch.toMat(Sink.ignore)(Keep.both)
      case REPORT_TO_ACTOR_SINK =>
        val reportTo: Option[ActorRef] = actorConfig.others.get(ActorType.ACTOR_SINK)
        val sink: Sink[TaggedWithType[Tag] with DataStore[V1], Future[Done]] = reportTo.map(x => actorSink.apply(x)).getOrElse(Sink.ignore)
        killSwitch.toMat(sink)(Keep.both)
      case e =>
        log.warn(s"return type '$e' not covered, using Sink.ignore")
        killSwitch.toMat(Sink.ignore)(Keep.both)
    }

  }

  /**
    * method to provide the runnable graph. Takes JobActorConfig to allow to pass info about executing actor and
    * optional additional actorRefs with clearly defined roles (e.g actorRef executing the runnableGraph, maybe THROUGHPUT actorRef to send information about throughput
    * in the distinct stages to
    *
    * @param actorConfig : providing the actually executing actor itself and optional additional actors with clearly defined roles (ActorType)
    * @param actorSystem : implicit actorSystem
    * @param mat         : implicit Materializer
    * @param ec          : implicit ExecutionContext
    * @return
    */
  def getRunnableGraph(actorConfig: JobActorConfig)(implicit actorSystem: ActorSystem, actorContext: ActorContext, mat: Materializer, ec: ExecutionContext): RunnableGraph[(UniqueKillSwitch, Future[Done])] = {
    val sinkInst: Sink[TaggedWithType[Tag] with DataStore[V1], (UniqueKillSwitch, Future[Done])] = getSink(actorConfig)
    RunnableGraph.fromGraph(GraphDSL.create(sinkInst) {
      implicit builder =>
        sink =>
          import GraphDSL.Implicits._
          val source: SourceShape[U] = builder.add(Source.fromIterator[U](() => supplier.iterator))
          val flowStage: FlowShape[U, TaggedWithType[Tag] with DataStore[V]] = builder.add(transformer)
          // stage sending message to separate actor if processingActorProps is set, otherwise
          // not changing the input element
          val sendFlow: Flow[TaggedWithType[Tag] with DataStore[V], TaggedWithType[Tag] with DataStore[V1], NotUsed] = processingActorProps.map(props => {
            Flow.fromFunction[TaggedWithType[Tag] with DataStore[V], TaggedWithType[Tag] with DataStore[V]](identity)
              .mapAsyncUnordered[TaggedWithType[Tag] with DataStore[V1]](config.requestParallelism)(x => {
                val sendToActor: ActorRef = actorContext.actorOf(props)
                implicit val timeout: Timeout = Timeout(waitTimePerElement)
                val responseFuture: Future[Any] = sendToActor ? x
                val mappedResponseFuture: Future[TaggedWithType[Tag] with DataStore[V1]] = responseFuture.transform({
                  case Success(value: TaggedWithType[Tag] with DataStore[V1]) => Success(value)
                  case Failure(exception) => Success[TaggedWithType[Tag] with DataStore[V1]](BadCorn(TaskFailType.FailedByException(exception)))
                  case e =>
                    Success(BadCorn(TaskFailType.UnknownResponseClass(e.getClass.getName)))
                })
                mappedResponseFuture
              })
          }).getOrElse(Flow.fromFunction[TaggedWithType[Tag] with DataStore[V], TaggedWithType[Tag] with DataStore[V1]](x => x.asInstanceOf[TaggedWithType[Tag] with DataStore[V1]]))
          val sendStage: FlowShape[TaggedWithType[Tag] with DataStore[V], TaggedWithType[Tag] with DataStore[V1]] = builder.add(sendFlow)
          // connect graph
          source ~> flowStage ~> sendStage ~> sink
          ClosedShape
    })
  }

}
