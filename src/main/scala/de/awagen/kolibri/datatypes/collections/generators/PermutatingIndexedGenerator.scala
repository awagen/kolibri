package de.awagen.kolibri.datatypes.collections.generators

import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.utils.PermutationUtils

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
