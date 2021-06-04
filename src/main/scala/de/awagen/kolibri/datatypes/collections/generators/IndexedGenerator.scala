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

import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1

/**
  * Generator providing generation logic elements of given type. Also allows splitting into smaller part given
  * start and end index. Provides method to get nr of elements, elements at position i and finally provides
  * an iterator over the elements of type T. Note that the idea here is that the objects generated by the generator
  * dont need to be in memory, but can be generated by the logic described within the iterator. Also, determining size
  * here should not require all elements to be loaded.
  *
  * Example use case: given Seq of Seq of different parameter values, find the i-th permutation of the parameter
  * set. If we have many parameter combinations, it would be memory-inefficient storing them all instead of providing logic
  * to generate on demand
  *
  * NOTE: this class could extend Iterable[T], yet this seems to cause
  * some problems with Kryo Serialization when assigning KolibriSerializable
  * to kryo serializer (e.g correct type of generator is not preserved);
  * should be solvable by proper config. Until then dont extend Iterable
  * (otherwise should see serialization log error that desired interface is not implemented but element being of
  * type scala.collection.immutable.$colon$colon)
  *
  * @tparam T
  */
trait IndexedGenerator[+T] extends KolibriSerializable {

  val nrOfElements: Int

  /**
    * Iterator over contained elements
    *
    * @return
    */
  def iterator: Iterator[T] = {
    new Iterator[T] {
      var currentPosition = 0

      override def hasNext: Boolean = currentPosition < nrOfElements

      override def next(): T = {
        val element = get(currentPosition)
        currentPosition += 1
        element.get
      }
    }
  }

  def size: Int = nrOfElements

  /**
    * create generator that only generates a part of the original generator.
    *
    * @param startIndex : startIndex (inclusive)
    * @param endIndex   : endIndex (exclusive)
    * @return generator generating the subpart of the generator as given by startIndex and endIndex
    */
  def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[T]

  /**
    * Get the index-th element
    *
    * @param index
    * @return
    */
  def get(index: Int): Option[T]

  /**
    * Provided a mapping function, create generator of new type where elements are created by current generator
    * and then mapped by the provided function
    *
    * @param f : mapping function
    * @tparam B : the type the original element type is mapped to
    * @return : new generator providing the new type
    */
  def mapGen[B](f: SerializableFunction1[T, B]): IndexedGenerator[B]

}
