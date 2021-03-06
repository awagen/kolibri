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


package de.awagen.kolibri.base.resources

import de.awagen.kolibri.base.resources.Resources.ResourceType.ResourceType
import de.awagen.kolibri.datatypes.io.KolibriSerializable

import scala.collection.mutable

object Resources {

  object ResourceType extends Enumeration {
    type ResourceType = Value

    val JUDGEMENTS_FILE: Value = Value

  }

  case class Resource(resourceType: ResourceType, identifier: String) extends KolibriSerializable

  trait WithResources extends KolibriSerializable {

    private[this] val resourceSet: mutable.Set[Resource] = mutable.Set.empty[Resource]

    def resources: Set[Resource] = resourceSet.toSet

    def addResource(resource: Resource): Unit = {
      resourceSet += resource
    }

  }

}
