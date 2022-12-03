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

package de.awagen.kolibri.base.cluster

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator.Update
import akka.cluster.ddata._
import akka.cluster.ddata.typed.scaladsl.Replicator.WriteLocal
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.stream.Materializer
import de.awagen.kolibri.base.actors.clusterinfo.DDResourceStateUtils.DD_BATCH_STATUS_ACTOR_REF_KEY
import de.awagen.kolibri.base.actors.clusterinfo.{BatchStateActor, LocalStateDistributorActor, ResourceToJobMappingClusterStateManagerActor}
import de.awagen.kolibri.base.actors.routing.RoutingActor
import de.awagen.kolibri.base.cluster.ClusterNodeObj.LOCAL_RESOURCES_ACTOR_NAME
import de.awagen.kolibri.base.config.AppProperties
import de.awagen.kolibri.base.config.AppProperties.config
import de.awagen.kolibri.base.config.AppProperties.config.{kolibriDispatcherName, node_roles, useRequestEventShardingAndEndpoints}
import de.awagen.kolibri.base.directives.RetrievalDirective.RetrievalDirective
import de.awagen.kolibri.base.http.server.HttpServer
import de.awagen.kolibri.base.http.server.routes.AnalysisRoutes.{getImproovingAndLoosing, getPartialResultsOverview, getResultBaseFolders, getSingleResult, getSingleResultFiltered, getValueVarianceFromDir}
import de.awagen.kolibri.base.http.server.routes.BaseRoutes
import de.awagen.kolibri.base.http.server.routes.BaseRoutes._
import de.awagen.kolibri.base.http.server.routes.DataRoutes._
import de.awagen.kolibri.base.http.server.routes.JobDefRoutes.getSearchEvaluationEndpointAndJobDef
import de.awagen.kolibri.base.http.server.routes.MetricRoutes.{getAvailableIRMetrics, getIRMetricJsonsFromReducedJsons}
import de.awagen.kolibri.base.http.server.routes.JobTemplateResourceRoutes.{getAvailableTemplatesByType, getJobTemplateByTypeAndIdentifier, getJobTemplateOverviewForType, getJobTemplateTypes, storeSearchEvaluationTemplate}
import de.awagen.kolibri.base.http.server.routes.StatusRoutes.{finishedJobStates, getAllJobWorkerStates, getJobStatus, getJobWorkerStatus, getRunningJobIds, health, jobStates, nodeState}
import de.awagen.kolibri.base.resources.RetrievalError
import de.awagen.kolibri.base.usecase.statesharding.actors.EventAggregatingActor
import de.awagen.kolibri.base.usecase.statesharding.routes.StateRoutes.{sendCombinedEvent, sendEntityEvent, sendKeyValueEvent}
import kamon.Kamon
import org.slf4j.{Logger, LoggerFactory}

import java.lang.management.ManagementFactory
import java.util.Objects
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object ClusterNodeObj {

  val LOCAL_RESOURCES_ACTOR_NAME = "localResources"

  def getResource[T](directive: RetrievalDirective[T]): Either[RetrievalError[T], T] = {
    ResourceToJobMappingClusterStateManagerActor.getResourceByStoreName[T](LOCAL_RESOURCES_ACTOR_NAME, directive)
  }

}

/**
 * App object to start a new cluster node
 */
object ClusterNode extends App {

  private[this] val logger: Logger = LoggerFactory.getLogger(ClusterNode.getClass.toString)



  // This line initializes all Kamon components
  // needs to happen before start of actor system
  Kamon.init()

  val mb = 1024 * 1024
  val memoryBean = ManagementFactory.getMemoryMXBean
  val xmx = memoryBean.getHeapMemoryUsage.getMax / mb
  val xms = memoryBean.getHeapMemoryUsage.getInit / mb
  logger.info("Initial Memory (xms) : {}mb", xms)
  logger.info("Max Memory (xmx) : {}mb", xmx)

  private[this] var setup: SystemSetup = _
  if (args.length > 0 && args(0).toBoolean) {
    startSystemSetup(None)
  }

  def getSystemSetup: SystemSetup = {
    if (Objects.nonNull(setup)) {
      setup
    }
    else {
      startSystemSetup(None)
      setup
    }
  }

  def startSystemSetup(route: Option[Route]): Unit = {
    synchronized {
      if (Objects.isNull(setup)) {
        setup = SystemSetup(route)
        logger.info("System setup initialized")
      }
      else {
        logger.warn("System setup already initialized, ignoring")
      }
    }
  }

  case class SystemSetup(route: Option[Route] = None) {
    // adds support for actors to a classic actor system and context

    import akka.actor.typed.scaladsl.adapter._

    implicit val actorSystem: ActorSystem = startSystem()
    implicit val mat: Materializer = Materializer(actorSystem)
    implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatchers.lookup(kolibriDispatcherName)

    // start cluster-sharding (right now we only need this if we make use of the state-sharding use-case)
    var sharding: ClusterSharding = _
    var EventAggregatingActorTypeKey: EntityTypeKey[EventAggregatingActor.RequestEvent] = _
    if (useRequestEventShardingAndEndpoints) {
      sharding = ClusterSharding(actorSystem.toTyped)
      EventAggregatingActorTypeKey = EntityTypeKey[EventAggregatingActor.RequestEvent]("contextAggregator")
      sharding
        .init(Entity(EventAggregatingActorTypeKey)(createBehavior = _ =>
          EventAggregatingActor.apply(EventAggregatingActor.RequestEventStore.empty, storeSequence = true, Seq.empty, maxEvents = 3)
        ))
    }

    // start replicator for distributed data on each node
    val ddReplicator: ActorRef = DistributedData.get(actorSystem).replicator
    implicit val ddSelfUniqueAddress: SelfUniqueAddress = DistributedData.get(actorSystem).selfUniqueAddress
    val localStateDistributorActor: ActorRef = actorSystem.actorOf(Props[LocalStateDistributorActor])
    // adding one localResourceManagerActor per node to cause per-node handling (e.g cases such as
    // clean up local resource when in whole cluster no job uses it anymore, such as within
    // FileBasedJudgementRepository)
    val localResourceManagerActor: ActorRef = actorSystem.actorOf(ResourceToJobMappingClusterStateManagerActor.props(LOCAL_RESOURCES_ACTOR_NAME))
    val isHttpServerNode: Boolean = node_roles.contains(config.HTTP_SERVER_ROLE)
    logger.info(s"Node roles: $node_roles")
    logger.info(s"isHttpServerNode: $isHttpServerNode")

    var batchStatusActor: Option[ActorRef] = None
    if (isHttpServerNode) {
      logger.info("Starting httpserver")
      // first create and register batch status actor
      // create BatchStateActor and publish the actor ref under respective topic
      batchStatusActor = Some(actorSystem.actorOf(BatchStateActor.props(10, 20)))
      val ddBatchStatusActorRefUpdate: Update[ORSet[ActorRef]] =
        Update[ORSet[ActorRef]](DD_BATCH_STATUS_ACTOR_REF_KEY, ORSet.empty[ActorRef], WriteLocal)(_ :+ batchStatusActor.get)
      ddReplicator ! ddBatchStatusActorRefUpdate
      // need to initialize the BaseRoutes to start Supervisor actor in current actorSystem
      BaseRoutes.init
      val commonRoute: Route = route.getOrElse(simpleHelloRoute ~ streamingUserRoutes ~ clusterStatusRoutee ~ killAllJobs
        ~ getJobStatus ~ killJob ~ getJobWorkerStatus ~ getRunningJobIds ~ executeDistributedPiCalculationExample
        ~ executeDistributedPiCalculationExampleWithoutSerialization ~ startSearchEval ~ startSearchEvalNoSerialize
        ~ startExecution ~ nodeState ~ jobStates ~ finishedJobStates ~ health ~ getAllJobWorkerStates
        ~ getJudgements ~ getAllJudgements ~ getJobTemplateOverviewForType ~ getJobTemplateByTypeAndIdentifier
        ~ getJobTemplateTypes ~ storeSearchEvaluationTemplate ~ startExecutionDefinition
        ~ getAllIndexedGeneratorInfosForFileData
        ~ getIndexedGeneratorInfoForValueSeqGenProviderSeqBody ~ getDataFilesByType
        ~ getValuesByTypeAndFile
        ~ getResultBaseFolders ~ getPartialResultsOverview ~ getSingleResult
        ~ getSingleResultFiltered ~ getImproovingAndLoosing ~ getValueVarianceFromDir
        ~ getExampleQueriesForValueSeqGenProviderSequence
        ~ getAvailableIRMetrics ~ getIRMetricJsonsFromReducedJsons
        ~ getSearchEvaluationEndpointAndJobDef
        ~ startQueryBasedReducedSearchEvalNoSerialize
        ~ getAvailableTemplatesByType
      )
      val usedRoute: Route = if (useRequestEventShardingAndEndpoints) {
        commonRoute ~ sendCombinedEvent ~ sendEntityEvent ~ sendKeyValueEvent
      } else commonRoute

      HttpServer.startHttpServer(usedRoute, interface = config.http_server_host, port = config.http_server_port).onComplete {
        case Success(serverBinding) => logger.info(s"listening to ${serverBinding.localAddress}")
        case Failure(error) => logger.info(s"error on server start: ${error.getMessage}")
      }
    }

    /**
     * @return
     */
    def startSystem(): ActorSystem = {
      val system = ActorSystem(config.applicationName, AppProperties.config.baseConfig)
      AkkaManagement(system).start()
      ClusterBootstrap(system).start()
      Cluster(system).registerOnMemberUp({
        if (config.startClusterSingletonRouter) {
          ClusterSingletonUtils.createClusterSingletonManager(system, RoutingActor.defaultProps)
        }
        logger.info("Cluster is up!")
      })
      system
    }
  }

}
