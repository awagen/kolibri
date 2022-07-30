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

import akka.actor.ActorRef
import akka.cluster.ddata.Replicator.Update
import akka.cluster.ddata._
import akka.cluster.ddata.typed.scaladsl.Replicator.WriteLocal
import de.awagen.kolibri.base.directives.{Resource, ResourceType}
import de.awagen.kolibri.base.directives.ResourceType.ResourceType
import org.slf4j.{Logger, LoggerFactory}

object DDResourceStateUtils {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  // identifier and key for the central actor reference to retrieve the batch updates
  val DD_BATCH_INFO_ACTOR_REF_ID: String = "ddBatchInfoActor"
  val DD_BATCH_STATUS_ACTOR_REF_KEY: Key[ORSet[ActorRef]] = ORSetKey.create(DD_BATCH_INFO_ACTOR_REF_ID)

  private[clusterinfo] def getJobMappingIdForResourceType(resourceType: ResourceType[_]): String = s"dd${resourceType.toString()}JobMapping"

  /**
   * NOTE: some loaded resources (such as judgement loading into AtomicMapPromiseStore) use the path of the resource
   * as identifiers, so to keep up the mechanism of being able to delete resources when no job is utilizing them anymore
   * it is advised to keep the identifier below as is, e.g as the identifier of the resource
   * @param resource
   * @return
   */
  private[clusterinfo] def getIdentifierForResource(resource: Resource[_]): String = {
    s"${resource.identifier}"
  }

  // identifier and key for mapping of resource type identifiers to jobs utilizing them
  val DD_RESOURCETYPE_TO_KEY_MAPPING: Map[ResourceType[_], Key[ORMap[String, ORSet[String]]]] = ResourceType.vals.map(value => {
    (value, ORMapKey.create[String, ORSet[String]](getJobMappingIdForResourceType(value)))
  }).toMap


  /**
   * Initializes the keys for all resource types
   *
   * @return
   */
  def ddMappingInit(): Seq[Update[ORMap[String, ORSet[String]]]] = {
    DD_RESOURCETYPE_TO_KEY_MAPPING.values.map(value => {
      Update[ORMap[String, ORSet[String]]](value, ORMap.empty[String, ORSet[String]], WriteLocal)(identity)
    }).toSeq
  }

  /**
   * Adding jobId to the resource type mapping for the given resource.
   *
   * @param selfAddress - self address of the node where this update is created
   * @param resource    - the resource for which to update the mapping
   * @param jobId       - the jobId to add to the set of job identifiers for the specified resource. Indicates which jobIds
   *                    are currently running and using this resource. The information of those mappings can be used per node
   *                    to clean up global resources (such as judgement storage) to avoid resources remaining in memory
   *                    when not needed anymore, while still avoiding too frequent cleanup
   * @return
   */
  def ddResourceJobMappingUpdateAdd(selfAddress: SelfUniqueAddress, resource: Resource[_], jobId: String): Option[Update[ORMap[String, ORSet[String]]]] = {
    val resourceIdentifier = getIdentifierForResource(resource)
    val addMap: ORMap[String, ORSet[String]] = ORMap.create()
      .put(selfAddress, resourceIdentifier, ORSet.create()
        .add(selfAddress, jobId)
      )
    val result: Option[Update[ORMap[String, ORSet[String]]]] = DD_RESOURCETYPE_TO_KEY_MAPPING.get(resource.resourceType)
      .map(key => {
        Update[ORMap[String, ORSet[String]]](key, ORMap.empty[String, ORSet[String]], WriteLocal)(map => map.merge(addMap))
      })
    result match {
      case Some(_) => result
      case None =>
        logger.warn(s"request to add job mapping for resource '$resource' failed, since no key found for resource type")
        result
    }
  }

  /**
   * Removing jobId from the resource type mapping for the given resource.
   *
   * @param resource    - the resource for which to update the mapping
   * @param jobId       - jobId to remove from mapping
   * @param selfAddress - self address of the node where this update is created
   * @return
   */
  def ddResourceJobMappingUpdateRemove(resource: Resource[_], jobId: String)(implicit selfAddress: SelfUniqueAddress): Option[Update[ORMap[String, ORSet[String]]]] = {
    val resourceIdentifier = getIdentifierForResource(resource)
    val result: Option[Update[ORMap[String, ORSet[String]]]] = DD_RESOURCETYPE_TO_KEY_MAPPING.get(resource.resourceType)
      .map(key => {
        Update[ORMap[String, ORSet[String]]](key, ORMap.empty[String, ORSet[String]], WriteLocal)(map => {
          // if the option is empty, we dont need to do anything
          // if contains value, update set with the passed jobId removed
          map.get(resourceIdentifier)
            .map(set => set.remove(jobId))
            .map(updatedSet => map.updated(selfAddress, resourceIdentifier, map.get(resourceIdentifier).getOrElse(ORSet.empty))(_ => updatedSet))
            .getOrElse(map)
        })
      })
    result match {
      case Some(_) => result
      case None =>
        logger.warn(s"request to remove job mapping for resource '$resource' failed, since no key found for resource type")
        result
    }

  }

}
