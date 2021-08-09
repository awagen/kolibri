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

/**
  * Generator that takes a sequence of generators and acts like a normal OneAfterAnotherIndexedGenerator, e.g
  * will generate the elements of each contained generator sequentially, thus the number of overall elements
  * is the sum of the elements of the single generators.
  * What differs here is the partitions function, which will keep the groups. This partitioning by this generator and still
  * keep logical groups within it, e.g where each generator passed reflects such as logical grouping
  * @param groups - the Seq of generators, where each generator represents a logical group that can be partitioned by
  * @tparam T - element type of the generators
  */
case class PartitionByGroupIndexedGenerator[T](groups: Seq[IndexedGenerator[T]]) extends IndexedGenerator[T] {
  val allElementSequentialGenerator: IndexedGenerator[T] = OneAfterAnotherIndexedGenerator(groups)
  override val nrOfElements: Int = allElementSequentialGenerator.nrOfElements

  override def partitions: IndexedGenerator[IndexedGenerator[T]] = ByFunctionNrLimitedIndexedGenerator.createFromSeq(groups)

  /**
    * create generator that only generates a part of the original generator.
    *
    * @param startIndex : startIndex (inclusive)
    * @param endIndex   : endIndex (exclusive)
    * @return generator generating the subpart of the generator as given by startIndex and endIndex
    */
  override def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[T] = allElementSequentialGenerator.getPart(startIndex, endIndex)

  /**
    * Get the index-th element
    *
    * @param index
    * @return
    */
  override def get(index: Int): Option[T] = allElementSequentialGenerator.get(index)

  /**
    * Provided a mapping function, create generator of new type where elements are created by current generator
    * and then mapped by the provided function
    *
    * @param f : mapping function
    * @tparam B : the type the original element type is mapped to
    * @return : new generator providing the new type
    */
  override def mapGen[B](f: SerializableCallable.SerializableFunction1[T, B]): IndexedGenerator[B] = allElementSequentialGenerator.mapGen(f)
}
