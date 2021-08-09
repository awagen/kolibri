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
  * Generator that just starts picking elements from the next generator when the requested element exceeds its own
  * elements, e.g just sequentially provides the elements of the distinct generators
  *
  * @param generators - list of generatprs
  * @tparam T - type of the single generators
  */
case class OneAfterAnotherIndexedGenerator[+T](generators: Seq[IndexedGenerator[T]]) extends IndexedGenerator[T] {
  override val nrOfElements: Int = generators.map(x => x.size).sum
  val generatorSizes: Seq[Int] = generators.map(generator => generator.size)
  val generatorStartEndIndices: Seq[(Int, Int)] = generatorSizes.indices.map(index => {
    if (index == 0) (0, generators.head.nrOfElements - 1)
    else {
      val elementsSoFar = generatorSizes.slice(0, index).sum
      (elementsSoFar, elementsSoFar + generatorSizes(index) - 1)
    }
  })


  /**
    * Given an elementIndex overall, find which generator index this corresponds to and which element of that generator is needed
    *
    * @param elementIndex - the index overall of the element needed
    * @return - Tuple containing first index of the generator needed and second the element index within that generator
    */
  def getGeneratorIndexAndGeneratorIndex(elementIndex: Int): Option[(Int, Int)] = {
    if (elementIndex < 0 || elementIndex >= nrOfElements) None
    else {
      val generatorIndex: Option[Int] = generatorStartEndIndices.indices.find(x => {
        val limits: (Int, Int) = generatorStartEndIndices(x)
        elementIndex >= limits._1 && elementIndex <= limits._2
      })
      generatorIndex.map(genIndex => {
        val generatorElementIndex = elementIndex - generatorSizes.slice(0, genIndex).sum
        (genIndex, generatorElementIndex)
      })
    }
  }


  /**
    * create generator that only generates a part of the original generator.
    *
    * @param startIndex : startIndex (inclusive)
    * @param endIndex   : endIndex (exclusive)
    * @return generator generating the subpart of the generator as given by startIndex and endIndex
    */
  override def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[T] = {
    // adjust startindex to its boundaries
    var start: Int = math.max(0, startIndex)
    if (start >= nrOfElements) start = math.max(0, nrOfElements - 1)
    // adjust the endIndex to its boundaries
    var endExcluded: Boolean = false
    var end: Int = if (endIndex >= nrOfElements) {
      endExcluded = true
      math.max(0, nrOfElements - 1)
    } else endIndex
    // if start and end are same, just return empty generator
    if (start == end) return OneAfterAnotherIndexedGenerator(Seq.empty)
    // exclude the endIndex
    if (end > 0 && !endExcluded) end -= 1
    // combine the generators such as covering the full range of the selected part,
    // which might involve taking full generators and cutting parts out of first and last generator
    val startGeneratorAndElementIndex: (Int, Int) = getGeneratorIndexAndGeneratorIndex(start).get
    val endGeneratorAndElementIndex: (Int, Int) = getGeneratorIndexAndGeneratorIndex(end).get
    val neededGenerators = generators.slice(startGeneratorAndElementIndex._1, endGeneratorAndElementIndex._1 + 1)
    // now get relevant part of the first and the last generator to yield the correct range
    if (startGeneratorAndElementIndex._1 == endGeneratorAndElementIndex._1) {
      val firstGenPart = neededGenerators.head.getPart(startGeneratorAndElementIndex._2, endGeneratorAndElementIndex._2 + 1)
      OneAfterAnotherIndexedGenerator(Seq(firstGenPart))
    }
    else {
      val firstGenPart = neededGenerators.head.getPart(startGeneratorAndElementIndex._2, neededGenerators.head.size)
      val lastGenPart = neededGenerators.last.getPart(0, endGeneratorAndElementIndex._2 + 1)
      OneAfterAnotherIndexedGenerator(Seq(firstGenPart) ++ neededGenerators.slice(1, neededGenerators.size - 1) ++ Seq(lastGenPart))
    }
  }

  /**
    * Get the index-th element overall. Picks the right generator and index within that generator
    * that corresponds to the needed element.
    *
    * @param index
    * @return
    */
  override def get(index: Int): Option[T] = {
    val generatorAndIndexTuple: Option[(Int, Int)] = getGeneratorIndexAndGeneratorIndex(index)
    generatorAndIndexTuple.flatMap(genAndIndexTuple => {
      generators(genAndIndexTuple._1).get(genAndIndexTuple._2)
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
  override def mapGen[B](f: SerializableCallable.SerializableFunction1[T, B]): IndexedGenerator[B] = {
    OneAfterAnotherIndexedGenerator[B](generators.map(generator => generator.mapGen(f)))
  }

}