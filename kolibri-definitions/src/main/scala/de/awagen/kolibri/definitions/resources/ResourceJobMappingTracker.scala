/**
 * Copyright 2022 Andreas Wagenmann
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


package de.awagen.kolibri.definitions.resources

import de.awagen.kolibri.definitions.directives.{Resource, ResourceType}
import de.awagen.kolibri.definitions.directives.ResourceType.ResourceType
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

class ResourceJobMappingTracker {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private[this] val resourceToJobMapping: mutable.Map[ResourceType[_], mutable.Map[String, Set[String]]] =
    mutable.Map.empty[ResourceType[_], mutable.Map[String, Set[String]]]

  private[resources] def resourceTypeExists(resource: Resource[_]): Boolean = {
    resourceToJobMapping.get(resource.resourceType).flatMap(idMap => idMap.get(resource.identifier)).nonEmpty
  }

  private[resources] def resourceExists(resource: Resource[_]): Boolean = {
    resourceToJobMapping.get(resource.resourceType).flatMap(idMap => idMap.get(resource.identifier)).nonEmpty
  }

  /**
   * Initializes the state with one map entry for each ResourceType
   */
  def init(): Unit = {
    ResourceType.vals.foreach(value => {
      logger.info(s"initializing resource job mapping for resource type '${value.toString()}'")
      resourceToJobMapping(value) = mutable.Map.empty[String, Set[String]]
    })
  }

  def getMappingForResourceType(resourceType: ResourceType[_]): Map[String, Set[String]] = resourceToJobMapping
    .get(resourceType).map(x => Map(x.toSeq:_*)).getOrElse(Map.empty[String, Set[String]])

  def getMappingsForAllResourceTypes: Map[ResourceType[_], Map[String, Set[String]]] = {
    resourceToJobMapping.keys.map(key => (key, getMappingForResourceType(key))).toMap
  }


  /**
   * Remove a particular resource from the mapping (meaning: remove resourceId from the mapping of its particular
   * ResourceType)
   * @param resource
   */
  def removeResource(resource: Resource[_]): Unit = {
    resourceToJobMapping.get(resource.resourceType).foreach(idMap => {
      logger.info(s"removing resource from resource-job mapping: $resource")
      idMap.remove(resource.identifier)
      logger.info(s"new state of resourceId-job mapping for resource '$resource': $idMap")
    })
  }

  /**
   * Removes a jobId from the job-assignments for a particular resourceId for particular ResourceType
   * @param resource
   * @param jobId
   */
  def removeJobFromResource(resource: Resource[_], jobId: String): Unit = {
    if (!resourceExists(resource)) {
      logger.warn(s"asked to remove jobId '$jobId' from resource '$resource' but key for resource does not exist")
      ()
    }
    else {
      logger.info(s"removing jobId '$jobId' from resource '$resource'")
      resourceToJobMapping(resource.resourceType)(resource.identifier) = resourceToJobMapping(resource.resourceType)(resource.identifier) - jobId
      if (resourceToJobMapping(resource.resourceType)(resource.identifier).isEmpty){
        resourceToJobMapping(resource.resourceType).remove(resource.identifier)
      }
    }
  }

  def removeJobFromAllResources(jobId: String): Unit = {
    resourceToJobMapping.keys.foreach(key => {
      resourceToJobMapping(key).keys.foreach(resourceIdKey => {
        resourceToJobMapping(key)(resourceIdKey) = resourceToJobMapping(key)(resourceIdKey) - jobId
      })
    })
  }

  def removeJobForResourceType(jobId: String, resourceType: ResourceType[_]): Unit = {
    resourceToJobMapping.get(resourceType).foreach(map => {
      map.keys.foreach(resourceIdKey => {
        removeJobFromResource(Resource(resourceType, resourceIdKey), jobId)
      })
    })
  }

  def updateJobMappingForResource(resource: Resource[_], newJobSet: Set[String]): Unit = {
    resourceToJobMapping.get(resource.resourceType).foreach(mapping => {
      logger.info(s"updating job mapping for resource '$resource': old = ${mapping(resource.identifier)}, new = ${newJobSet}")
      mapping(resource.identifier) = newJobSet
    })
  }

  /**
   * Adds a jobId to the mapping for a particular Resource
   * @param resourceType
   * @param resourceId
   * @param jobId
   */
  def addTrackingForResourceAndJob(resource: Resource[_], jobId: String): Unit = {
    if (!resourceToJobMapping.keys.toSeq.contains(resource.resourceType)) {
      resourceToJobMapping(resource.resourceType) = mutable.Map.empty[String, Set[String]]
    }
    logger.info(s"adding jobId '$jobId' to mapping for resource '$resource'")
    resourceToJobMapping(resource.resourceType)(resource.identifier) = resourceToJobMapping(resource.resourceType).getOrElse(resource.identifier, Set.empty) + jobId
  }

  /**
   * Updates the whole mapping for a given ResourceType. Before updating removes the keys of the passed map
   * whose value is empty set
   * @param resourceType
   * @param newResourceIdToJobMapping
   */
  def setMappingForResourceType(resourceType: ResourceType[_], newResourceIdToJobMapping: Map[String, Set[String]]): Unit = {
    val cleanedState: Map[String, Set[String]] = newResourceIdToJobMapping.filter(x => x._2.nonEmpty)
    logger.info(s"setting mapping for resourceType '$resourceType': $cleanedState")
    resourceToJobMapping(resourceType) = mutable.Map(cleanedState.toSeq:_*)
  }


}
