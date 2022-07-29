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

import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.utils.PermutationUtils

/**
  * Takes a number of generators of same type and returns generator that generates all permutations of all the values
  * within the distinct generators, keeping the position in the resulting Seq.
  *
  * @param generators - the Seq of generators
  * @tparam T - type of the elements
  */
case class PermutatingIndexedGenerator[+T](generators: Seq[IndexedGenerator[T]]) extends IndexedGenerator[Seq[T]] {

  private[generators] def sizeFunc[B >: T]: SerializableFunction1[IndexedGenerator[B], Int] = new SerializableFunction1[IndexedGenerator[B], Int] {
    override def apply(v1: IndexedGenerator[B]): Int = v1.nrOfElements
  }
  val elementsPerParameter: Seq[Int] = generators.map(sizeFunc)
  override val nrOfElements: Int = elementsPerParameter.product

  override def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[Seq[T]] = {
    val getFunc: SerializableFunction1[Int, Option[Seq[T]]] = new SerializableFunction1[Int, Option[Seq[T]]] {
      override def apply(v1: Int): Option[Seq[T]] = get(startIndex + v1)
    }
    assert(startIndex >= 0 && startIndex < nrOfElements)
    val end: Int = math.min(nrOfElements, endIndex)
    ByFunctionNrLimitedIndexedGenerator(end - startIndex, getFunc)
  }

  override def get(index: Int): Option[Seq[T]] = {
    val indicesOpt: Option[Seq[Int]] = PermutationUtils.findNthElementForwardCalc(elementsPerParameter, index)
    def mapFunc(seq: Seq[Int]): SerializableFunction1[Int, T] = new SerializableFunction1[Int, T] {
      override def apply(v1: Int): T = {
        generators(v1).get(seq(v1)).get
      }
    }
    val mapFunc2 = new SerializableFunction1[Seq[Int], Seq[T]] {
      override def apply(v1: Seq[Int]): Seq[T] = {
        generators.indices.map(mapFunc(v1))
      }
    }
    indicesOpt.map(mapFunc2)
  }

  override def mapGen[B](f: SerializableFunction1[Seq[T], B]): IndexedGenerator[B] = {
    val getAndMapFunction = new SerializableFunction1[Int, Option[B]] {
      override def apply(v1: Int): Option[B] = get(v1).map(f)
    }
    new ByFunctionNrLimitedIndexedGenerator[B](nrOfElements, getAndMapFunction)
  }
}
