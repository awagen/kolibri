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

package de.awagen.kolibri.datatypes.collections.generators

import de.awagen.kolibri.datatypes.types.SerializableCallable

case class BatchByGeneratorIndexedGenerator[+T](generators: Seq[IndexedGenerator[T]], batchByIndex: Int) extends IndexedGenerator[IndexedGenerator[Seq[T]]] {
  assert(batchByIndex < generators.size, s"given index of generator to batch by ($batchByIndex) is not within indices of passed generators" +
    s" with maxIndex ${generators.size - 1}")

  override val nrOfElements: Int = generators(batchByIndex).partitions.size

  /**
    * create generator that only generates a part of the original generator.
    *
    * @param startIndex : startIndex (inclusive)
    * @param endIndex   : endIndex (exclusive)
    * @return generator generating the subpart of the generator as given by startIndex and endIndex
    */
  override def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[IndexedGenerator[Seq[T]]] = {
    val start = math.min(math.max(0, startIndex), nrOfElements - 1)
    val end = math.min(math.max(0, endIndex), nrOfElements)
    // find the partition groups, e.g each generator corresponds to a grouping and the batch is defined by adding the
    // generator corresponding to the n-th batch (n-th generator in the Seq) to the generators to permutate over in the batch
    val partitionGroups: Seq[IndexedGenerator[T]] = generators(batchByIndex).partitions.getPart(start, end).iterator.toSeq
    BatchByGeneratorIndexedGenerator(generators.indices.map({
      // keep the groupings. Mapping to PartitionByGroupIndexedGenerator will only make a difference if the partition
      // generators are bigger than single element
      case e if e == batchByIndex => PartitionByGroupIndexedGenerator(partitionGroups)
      case e => generators(e)
    }), batchByIndex)
  }

  /**
    * Get the index-th element
    *
    * @param index
    * @return
    */
  override def get(index: Int): Option[IndexedGenerator[Seq[T]]] = {
    generators(batchByIndex).get(index).map(x => {
      PermutatingIndexedGenerator(generators.indices.map({
        case e if e == batchByIndex => ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq(x))
        case e => generators(e)
      }))
    })
  }

  /**
    * Provided a mapping function, create generator of new type where elements are created by current generator
    * and then mapped by the provided function
    *
    * @param f : mapping function
    * @tparam B : the type the original element type is mapped to
    * @return : new generator providing the new type
    */
  override def mapGen[B](f: SerializableCallable.SerializableFunction1[IndexedGenerator[Seq[T]], B]): IndexedGenerator[B] = {
    new ByFunctionNrLimitedIndexedGenerator[B](nrOfElements, x => get(x).map(f))
  }
}
