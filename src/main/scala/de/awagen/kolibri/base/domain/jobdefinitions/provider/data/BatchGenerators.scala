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
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValuesImplicits.OrderedMultiValuesToParameterIterator
import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore}
import de.awagen.kolibri.datatypes.tagging.MapImplicits.MutableTaggedMap
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.tagging.TypeTaggedMapImplicits.{TaggedTypeTaggedMap, _}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.utils.OrderedMultiValuesBatchUtils

import scala.collection.mutable


object BatchGenerators {

  trait IndexedBatchGenerator[T, V] extends KolibriSerializable {

    def batchFunc: SerializableFunction1[T, IndexedGenerator[Batch[V]]]

  }

  trait IndexedTypeTaggedBatchGenerator[T, V <: TaggedTypeTaggedMap] extends IndexedBatchGenerator[T, V]

  case class OrderedMultiValuesBatchGenerator(paramNameToSplitBy: String) extends IndexedBatchGenerator[OrderedMultiValues, MutableTaggedMap[String, Seq[Any]]] {
    override def batchFunc: SerializableFunction1[OrderedMultiValues, IndexedGenerator[Batch[MutableTaggedMap[String, Seq[Any]]]]] = {
      new SerializableFunction1[OrderedMultiValues, IndexedGenerator[Batch[MutableTaggedMap[String, Seq[Any]]]]] {
        override def apply(v1: OrderedMultiValues): IndexedGenerator[Batch[MutableTaggedMap[String, Seq[Any]]]] = {
          val seqByQuery: Seq[OrderedMultiValues] = OrderedMultiValuesBatchUtils.splitIntoBatchByParameter(v1, paramNameToSplitBy).toSeq
          ByFunctionNrLimitedIndexedGenerator(seqByQuery.size, batchNr => Some(Batch(batchNr, seqByQuery(batchNr).toParamNameValuesMapIndexedGenerator.mapGen(map => new MutableTaggedMap[String, Seq[Any]](new MutableTaggedMap(mutable.Map(map.toSeq: _*)))))))
        }
      }
    }

  }

  case class OrderedMultiValuesTypedTagBatchGenerator(paramNameToSplitBy: String) extends IndexedBatchGenerator[OrderedMultiValues, TypeTaggedMap with TaggedWithType[Tag]] {
    override def batchFunc: SerializableFunction1[OrderedMultiValues, IndexedGenerator[Batch[TypeTaggedMap with TaggedWithType[Tag]]]] = {
      new SerializableFunction1[OrderedMultiValues, IndexedGenerator[Batch[TypeTaggedMap with TaggedWithType[Tag]]]] {
        override def apply(v1: OrderedMultiValues): IndexedGenerator[Batch[TypeTaggedMap with TaggedWithType[Tag]]] = {
          val seqByQuery: Seq[OrderedMultiValues] = OrderedMultiValuesBatchUtils.splitIntoBatchByParameter(v1, paramNameToSplitBy).toSeq
          ByFunctionNrLimitedIndexedGenerator(seqByQuery.size, batchNr => Some(Batch(batchNr, seqByQuery(batchNr).toParamNameValuesMapIndexedGenerator
            .mapGen(map => TypedMapStore(mutable.Map(DataKeys.TAGGED_MAP.typed -> map)).toTaggedWithTypeMap))))
        }
      }
    }
  }

  /**
    * int number batch generator
    *
    * @param elementsToSplitBy
    */
  case class IntNumberBatchGenerator(elementsToSplitBy: Int) extends IndexedBatchGenerator[Int, Int] {
    override def batchFunc: SerializableFunction1[Int, IndexedGenerator[Batch[Int]]] = new SerializableFunction1[Int, IndexedGenerator[Batch[Int]]] {
      override def apply(nrOfElements: Int): IndexedGenerator[Batch[Int]] = {
        val remainingPartialBatchSize = nrOfElements % elementsToSplitBy
        val nrBatches = nrOfElements / elementsToSplitBy + (if (remainingPartialBatchSize == 0) 0 else 1)
        val elementsLastBatch = if (remainingPartialBatchSize == 0) elementsToSplitBy else remainingPartialBatchSize
        val genFunc: SerializableFunction1[Int, Option[Int]] = new SerializableFunction1[Int, Option[Int]] {
          override def apply(v1: Int): Option[Int] = Some(v1)
        }
        val func: SerializableFunction1[Int, Option[Batch[Int]]] = new SerializableFunction1[Int, Option[Batch[Int]]] {
          override def apply(v1: Int): Option[Batch[Int]] = {
            val nrElementsCurrentBatch = if (v1 == nrBatches - 1) elementsLastBatch else elementsToSplitBy
            Some(value = Batch[Int](v1, ByFunctionNrLimitedIndexedGenerator(
              nrOfElements = nrElementsCurrentBatch,
              genFunc = genFunc)))
          }
        }
        ByFunctionNrLimitedIndexedGenerator(nrOfElements = nrBatches, func)
      }
    }
  }

}

