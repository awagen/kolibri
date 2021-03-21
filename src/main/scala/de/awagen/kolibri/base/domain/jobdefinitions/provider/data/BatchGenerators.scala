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

package de.awagen.kolibri.base.domain.jobdefinitions.provider.data

import de.awagen.kolibri.base.domain.jobdefinitions.Batch
import de.awagen.kolibri.datatypes.collections.{BaseIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValuesImplicits.OrderedMultiValuesToParameterIterator
import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore}
import de.awagen.kolibri.datatypes.tagging.MapImplicits.MutableTaggedMap
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.tagging.TypeTaggedMapImplicits.{TaggedTypeTaggedMap, _}
import de.awagen.kolibri.datatypes.utils.OrderedMultiValuesBatchUtils

import scala.collection.mutable


object BatchGenerators {

  trait IndexedBatchGenerator[T, V] {

    def batchFunc: T => IndexedGenerator[Batch[V]]

  }

  trait IndexedTypeTaggedBatchGenerator[T, V <: TaggedTypeTaggedMap] extends IndexedBatchGenerator[T, V]

  case class TaggedWrapper[+T](value: T, batch: Int) extends TaggedWithType[Tag]


  case class OrderedMultiValuesBatchGenerator(paramNameToSplitBy: String) extends IndexedBatchGenerator[OrderedMultiValues, MutableTaggedMap[String, Seq[Any]]] {
    override def batchFunc: OrderedMultiValues => IndexedGenerator[Batch[MutableTaggedMap[String, Seq[Any]]]] = x => {
      val seqByQuery: Seq[OrderedMultiValues] = OrderedMultiValuesBatchUtils.splitIntoBatchByParameter(x, paramNameToSplitBy).toSeq
      BaseIndexedGenerator(seqByQuery.size, batchNr => Some(Batch(batchNr, seqByQuery(batchNr).toParamNameValuesMapIndexedGenerator.mapGen(map => new MutableTaggedMap[String, Seq[Any]](new MutableTaggedMap(mutable.Map(map.toSeq: _*)))))))
    }
  }

  case class OrderedMultiValuesTypedTagBatchGenerator(paramNameToSplitBy: String) extends IndexedBatchGenerator[OrderedMultiValues, TypeTaggedMap with TaggedWithType[Tag]] {
    override def batchFunc: OrderedMultiValues => IndexedGenerator[Batch[TypeTaggedMap with TaggedWithType[Tag]]] = x => {
      val seqByQuery: Seq[OrderedMultiValues] = OrderedMultiValuesBatchUtils.splitIntoBatchByParameter(x, paramNameToSplitBy).toSeq
      BaseIndexedGenerator(seqByQuery.size, batchNr => Some(Batch(batchNr, seqByQuery(batchNr).toParamNameValuesMapIndexedGenerator
        .mapGen(map => TypedMapStore(mutable.Map(DataKeys.TAGGED_MAP.typed -> map)).toTaggedWithTypeMap))))
    }
  }

  /**
    * int number batch generator, providing NestedIndexedIterable of TaggedWrapper[Int]
    *
    * @param elementsToSplitBy
    */
  case class IntNumberBatchGenerator(elementsToSplitBy: Int) extends IndexedBatchGenerator[Int, TaggedWrapper[Int]] {
    override def batchFunc: Int => IndexedGenerator[Batch[TaggedWrapper[Int]]] = nrOfElements => {
      val remainingPartialBatchSize = nrOfElements % elementsToSplitBy
      val nrBatches = nrOfElements / elementsToSplitBy + (if (remainingPartialBatchSize == 0) 0 else 1)
      val elementsLastBatch = if (remainingPartialBatchSize == 0) elementsToSplitBy else remainingPartialBatchSize
      BaseIndexedGenerator(nrOfElements = nrBatches,
        x => Some(value = Batch(x, BaseIndexedGenerator(
          nrOfElements = if (x == nrBatches - 1) elementsLastBatch else elementsToSplitBy,
          genFunc = y => Some(TaggedWrapper[Int](y, x))))))
    }
  }

}

