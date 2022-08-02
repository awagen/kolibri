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


package de.awagen.kolibri.base.actors.clusterinfo

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.ddata.Replicator.{Subscribe, Update}
import akka.cluster.ddata.typed.scaladsl.Replicator.WriteLocal
import akka.cluster.ddata.{Key, ORMap, ORSet}
import de.awagen.kolibri.base.actors.clusterinfo.ResourceToJobMappingClusterStateManagerActor._
import de.awagen.kolibri.base.cluster.ClusterNode
import de.awagen.kolibri.base.directives.ResourceDirectives.ResourceDirective
import de.awagen.kolibri.base.directives.ResourceType.ResourceType
import de.awagen.kolibri.base.directives.RetrievalDirective.RetrievalDirective
import de.awagen.kolibri.base.directives.{Resource, ResourceType}
import de.awagen.kolibri.base.resources._
import de.awagen.kolibri.base.usecase.searchopt.provider.FileBasedJudgementRepository
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

object ResourceToJobMappingClusterStateManagerActor {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def props(name: String): Props = Props(ResourceToJobMappingClusterStateManagerActor(name))

  trait LocalResourceManagerMsg extends KolibriSerializable

  case object GetExistingResourceTypeToResourceIdToJobMapping extends LocalResourceManagerMsg

  case class ExistingResourceTypeToResourceIdToJobMapping(mapping: Map[ResourceType[_], Map[String, Set[String]]]) extends LocalResourceManagerMsg

  case class RemoveValueFromAllMappings(ddResourceKey: Key[ORMap[String, ORSet[String]]], jobId: String) extends LocalResourceManagerMsg

  case class ProcessResourceDirectives(directives: Seq[ResourceDirective[_]], jobId: String) extends LocalResourceManagerMsg

  case class ProcessedResourceDirectives(directives: Seq[ResourceDirective[_]], states: Seq[ResourceState], jobId: String)

  private[clusterinfo] val nameToResourceStoreMapping: mutable.Map[String, ResourceStore] = mutable.Map.empty

  // public retrieval method
  def getResourceByStoreName[T](storeName: String, directive: RetrievalDirective[T]): Either[RetrievalError[T], T] = {
    logger.debug(s"requesting directive '$directive' for storeName '$storeName'")
    logger.debug(s"current state of stores: $nameToResourceStoreMapping'")
    nameToResourceStoreMapping.get(storeName).map(store => store.handleRetrievalDirective(directive))
      .getOrElse(Left(RetrievalError(directive, ResourceNotFound)))
  }

}


/**
 * Actor keeps track of distributed state messages of resource-jobId mappings.
 * In case no job is assigned to a resource anymore, data can be removed to avoid piling up of extensive state in memory.
 * This actor also takes care of this data removal.
 */
case class ResourceToJobMappingClusterStateManagerActor(name: String) extends Actor with ActorLogging {

  var resourceIdToJobMapping: ResourceJobMappingTracker = new ResourceJobMappingTracker()
  resourceIdToJobMapping.init()
  val resourceStore: ResourceStore = new ResourceStore()
  nameToResourceStoreMapping(name) = resourceStore

  // subscribe to receive replication messages for the mapping of used resource types to the jobs
  DDResourceStateUtils.DD_RESOURCETYPE_TO_KEY_MAPPING.values.foreach(value => {
    ClusterNode.getSystemSetup.ddReplicator ! Subscribe(value, self)
  })

  // set up partial function to handle all messages for the known keys
  def ddReceive: Receive = DistributedDataActorHelper.multipleStateChangeReceive[ORMap[String, ORSet[String]]](
    DDResourceStateUtils.DD_RESOURCETYPE_TO_KEY_MAPPING.map(resourceTypeAndValue => {
      (resourceTypeAndValue._2, resourceTypeAndValue._2.id, valueHandleFunc(resourceTypeAndValue._1))
    }).toSeq)

  def valueHandleFunc(resourceType: ResourceType[_]): ORMap[String, ORSet[String]] => Unit = map => {
    // pick the updated mapping
    val value: Map[String, Set[String]] = map.entries.map(x => (x._1, x._2.elements))
    log.debug(s"current value: $resourceIdToJobMapping, new value: $value")
    // check for each map root key whether Set is empty or missing now but existed before
    // and then make remove call to the actual resource where needed
    val keysForResourceType = resourceIdToJobMapping.getMappingForResourceType(resourceType).keySet
    // update the current job assignments
    resourceIdToJobMapping.setMappingForResourceType(resourceType, value)
    val updatedKeysForResourceType = resourceIdToJobMapping.getMappingForResourceType(resourceType).keySet
    val keysForResourceRemoval: Set[String] = keysForResourceType.diff(updatedKeysForResourceType)
    resourceType match {
      case ResourceType.MAP_STRING_TO_DOUBLE_VALUE =>
        keysForResourceRemoval.foreach(key => {
          log.info(s"Calling key remove on file-based judgement repository '${Resource(resourceType, key)}'")
          // TODO: resourceStore should replace the direct call to FileBasedJudgementRepository below,
          // but temporarily we keep it till we moved the logic
          FileBasedJudgementRepository.remove(key)
        })
      case _ => // do nothing
    }
    keysForResourceRemoval.foreach(key => {
      log.info(s"Calling key remove on resource '${Resource(resourceType, key)}'")
      resourceStore.removeResource(Resource(resourceType, key))
    })
  }

  override def receive: Receive = ddReceive.orElse[Any, Unit] {
    case e: ProcessResourceDirectives =>
      val senderRef = sender()
      log.info(s"processing resource directives: ${e.directives}")
      val handleStates: Seq[ResourceState] = e.directives.map(directive => {
        val status: ResourceState = resourceStore.handleResourceDirective(directive)
        if (status == ResourceOK) {
          resourceIdToJobMapping.addTrackingForResourceAndJob(directive.resource, e.jobId)
        }
        status
      })
      log.info(s"processing resource directives done. Passed directives: '${e.directives}', result states: '${handleStates}''")
      // sending back the the results for the requested resource directives
      senderRef ! ProcessedResourceDirectives(e.directives, handleStates, e.jobId)
    case GetExistingResourceTypeToResourceIdToJobMapping =>
      sender() ! ExistingResourceTypeToResourceIdToJobMapping(resourceIdToJobMapping.getMappingsForAllResourceTypes)
    case msg@RemoveValueFromAllMappings(resourceKey, jobId) =>
      // check in which mappings jobId occurs, and for each root key send remove update to
      // replicator
      log.info(s"received removal message: $msg")
      log.debug(s"current state: ${resourceIdToJobMapping.getMappingsForAllResourceTypes}")
      DDResourceStateUtils.DD_RESOURCETYPE_TO_KEY_MAPPING.filter(x => resourceKey == x._2)
        .foreach(resourceTypeAndKey => {
          val mappingsForResourceType: Map[String, Set[String]] = resourceIdToJobMapping.getMappingForResourceType(resourceTypeAndKey._1)
          mappingsForResourceType.filter(x => x._2.contains(jobId))
            .map(keyValue => {
              val newKeyValue = (keyValue._1, keyValue._2 - jobId)
              log.debug(s"modifying mapping for resourceType '${resourceTypeAndKey._1}': '$keyValue' to '$newKeyValue'")
              resourceIdToJobMapping.updateJobMappingForResource(Resource(resourceTypeAndKey._1, newKeyValue._1), newKeyValue._2)
              newKeyValue
            })
            .foreach(newKeyValue => {
              log.debug(s"removing jobId '$jobId' from mapping for key '${newKeyValue._1}'")
              ClusterNode.getSystemSetup.ddReplicator !
                Update[ORMap[String, ORSet[String]]](resourceKey, ORMap.empty[String, ORSet[String]], WriteLocal)(map => {
                  var newMap = map
                  if (newKeyValue._2.isEmpty) {
                    newMap = newMap.remove(newKeyValue._1)(ClusterNode.getSystemSetup.ddSelfUniqueAddress)
                    log.debug(s"prep of new ORMap, current value: ${map.entries}, new value: ${newMap.entries}")
                    newMap
                  }
                  else {
                    map.updated(ClusterNode.getSystemSetup.ddSelfUniqueAddress, newKeyValue._1, ORSet.empty)(currentSetValue => {
                      log.debug(s"prep of new ORSet, current value: ${currentSetValue.elements}")
                      var set: ORSet[String] = currentSetValue
                      currentSetValue.elements.diff(newKeyValue._2)
                        .foreach(x => set = set.remove(x)(ClusterNode.getSystemSetup.ddSelfUniqueAddress))
                      log.debug(s"done prepping of new ORSet, result: ${set.elements}")
                      set
                    })
                  }
                })
            })
        })

  }
}
