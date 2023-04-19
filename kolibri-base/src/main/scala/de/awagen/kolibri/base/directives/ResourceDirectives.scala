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


package de.awagen.kolibri.base.directives

import de.awagen.kolibri.base.directives.ExpirePolicy.ExpirePolicy
import de.awagen.kolibri.base.directives.ResourceType.ResourceType
import de.awagen.kolibri.base.directives.RetrievalDirective.RetrievalDirective
import de.awagen.kolibri.base.resources.RetrievalError
import de.awagen.kolibri.base.usecase.searchopt.provider.JudgementProvider
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.types.ClassTyped
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier

import scala.collection.mutable


object ResourceType extends Enumeration {
  type ResourceType[+T] = Val[T]

  type JudgementProviderResourceType = Val[JudgementProvider[Double]]
  type MapStringDoubleResourceType = Val[Map[String, Double]]
  type MapStringGeneratorStringResourceType = Val[Map[String, IndexedGenerator[String]]]
  type GeneratorStringResourceType = Val[IndexedGenerator[String]]

  case class Val[+T](classTyped: ClassTyped[T]) extends super.Val

  val JUDGEMENT_PROVIDER: JudgementProviderResourceType = Val(ClassTyped[JudgementProvider[Double]])
  val MAP_STRING_TO_DOUBLE_VALUE: MapStringDoubleResourceType = Val(ClassTyped[Map[String, Double]])
  val MAP_STRING_TO_STRING_VALUES: MapStringGeneratorStringResourceType = Val(ClassTyped[Map[String, IndexedGenerator[String]]])
  val STRING_VALUES: GeneratorStringResourceType = Val(ClassTyped[IndexedGenerator[String]])

  def vals: Seq[Val[_]] = Seq(JUDGEMENT_PROVIDER, MAP_STRING_TO_DOUBLE_VALUE, MAP_STRING_TO_STRING_VALUES, STRING_VALUES)
}

object ExpirePolicy extends Enumeration {
  type ExpirePolicy = Value

  val ON_JOB_END: Value = Value
}


case class Resource[+T](resourceType: ResourceType[T], identifier: String) extends KolibriSerializable

trait WithResources extends KolibriSerializable {

  private[this] val resourceSet: mutable.Set[Resource[_]] = mutable.Set.empty[Resource[_]]

  def resources: Set[Resource[_]] = resourceSet.toSet

  def addResource(resource: Resource[_]): Unit = {
    resourceSet += resource
  }

}

trait ResourceProvider {

  def getResource[T](directive: RetrievalDirective[T]): Either[RetrievalError[T], T]

}


/**
 * Resource load instructions
 */
object ResourceDirectives {

  trait ResourceDirective[+T] extends KolibriSerializable {
    def resource: Resource[T]

    def expirePolicy: ExpirePolicy

    def getResource: T
  }

  case class GenericResourceDirective[+T](resource: Resource[T], supplier: SerializableSupplier[T], expirePolicy: ExpirePolicy = ExpirePolicy.ON_JOB_END) extends ResourceDirective[T] {
    override def getResource: T = supplier.apply()
  }

  def getDirective[T](supplier: SerializableSupplier[T], resource: Resource[T]): ResourceDirective[T] = {
    GenericResourceDirective(resource, supplier)
  }

}
