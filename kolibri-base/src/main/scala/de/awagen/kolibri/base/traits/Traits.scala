package de.awagen.kolibri.base.traits

import de.awagen.kolibri.base.traits.Traits.ResourceType.ResourceType
import de.awagen.kolibri.datatypes.io.KolibriSerializable

import scala.collection.mutable

object Traits {

  trait WithBatchNr extends KolibriSerializable {

    def batchNr: Int

  }

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
