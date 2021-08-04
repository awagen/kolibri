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

  val elementsPerParameter: Seq[Int] = generators.map(x => x.nrOfElements)
  override val nrOfElements: Int = elementsPerParameter.product

  override def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[Seq[T]] = {
    assert(startIndex >= 0 && startIndex < nrOfElements)
    val end: Int = math.min(nrOfElements, endIndex)
    ByFunctionNrLimitedIndexedGenerator(end - startIndex, x => get(startIndex + x))
  }

  override def get(index: Int): Option[Seq[T]] = {
    val indicesOpt: Option[Seq[Int]] = PermutationUtils.findNthElementForwardCalc(elementsPerParameter, index)
    indicesOpt.map(x => {
      generators.indices.map(index => generators(index).get(x(index)).get)
    })
  }

  override def mapGen[B](f: SerializableFunction1[Seq[T], B]): IndexedGenerator[B] = {
    new ByFunctionNrLimitedIndexedGenerator[B](nrOfElements, x => get(x).map(f))
  }
}
