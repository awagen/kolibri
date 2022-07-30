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


package de.awagen.kolibri.base.resources

import de.awagen.kolibri.base.directives.Resource
import de.awagen.kolibri.base.directives.ResourceDirectives.ResourceDirective
import de.awagen.kolibri.base.directives.ResourceType.ResourceType
import de.awagen.kolibri.base.directives.RetrievalDirective.RetrievalDirective
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

sealed trait RetrievalFailCause extends KolibriSerializable

case class RetrievalFailedByException(exception: Exception) extends RetrievalFailCause

case object ResourceNotFound extends RetrievalFailCause

sealed trait ResourceState extends KolibriSerializable

case object ResourceOK extends ResourceState

case object ResourceAlreadyExists extends ResourceState

case class ResourceFailedByException(exception: Exception) extends ResourceState

case class RetrievalError[+T](directive: RetrievalDirective[T], cause: RetrievalFailCause)


/**
 * Storage for data to be available throughout the instance.
 * We can assume its functions dont need to be threadsafe, since
 * we expect one resource actor to handle its state and manage
 * requests to add or modify resources
 */
class ResourceStore {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private[this] val resourceMap: mutable.Map[ResourceType[_], mutable.Map[String, Any]] =
    mutable.Map.empty[ResourceType[_], mutable.Map[String, Any]]

  def resourceExists(resource: Resource[_]): Boolean = {
    resourceMap.get(resource.resourceType).flatMap(idMap => idMap.get(resource.identifier)).nonEmpty
  }

  def removeResource(resource: Resource[_]): Unit = {
    resourceMap.get(resource.resourceType).foreach(idMap => idMap.remove(resource.identifier))
  }

  def handleResourceDirective[T](directive: ResourceDirective[T]): ResourceState = {
    try {
      if (resourceExists(Resource(directive.resourceType, directive.resourceId))) {
        logger.warn(s"resource loading for directive '$directive' requested but already exists, ignoring")
        ResourceAlreadyExists
      }
      else {
        val value: T = directive.getResource
        if (!resourceMap.keys.toSeq.contains(directive.resourceType)) {
          resourceMap(directive.resourceType) = mutable.Map.empty[String, Any]
        }
        resourceMap(directive.resourceType)(directive.resourceId) = value
        ResourceOK
      }
    }
    catch {
      case e: Exception =>
        logger.warn(s"was unable to handle resource directive: ${directive}", e)
        ResourceFailedByException(e)
    }
  }

  def handleRetrievalDirective[T](directive: RetrievalDirective[T]): Either[RetrievalError[T], T] = {
    val retrievedOpt: Option[Any] = resourceMap.get(directive.resourceType).flatMap(idMap => idMap.get(directive.resourceId))
    retrievedOpt match {
      case None => Left(RetrievalError[T](directive, ResourceNotFound))
      case Some(e) =>
        try {
          Right(e.asInstanceOf[T])
        }
        catch {
          case e: Exception => Left(RetrievalError(directive, RetrievalFailedByException(e)))
        }
    }
  }

}
