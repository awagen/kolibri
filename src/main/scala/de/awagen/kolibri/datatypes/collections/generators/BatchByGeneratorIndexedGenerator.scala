package de.awagen.kolibri.datatypes.collections.generators

import de.awagen.kolibri.datatypes.types.SerializableCallable

case class BatchByGeneratorIndexedGenerator[+T](generators: Seq[IndexedGenerator[T]], batchByIndex: Int) extends IndexedGenerator[IndexedGenerator[Seq[T]]] {
  assert(batchByIndex < generators.size, s"given index of generator to batch by ($batchByIndex) is not within indices of passed generators" +
    s" with maxIndex ${generators.size - 1}")

  override val nrOfElements: Int = generators(batchByIndex).size

  /**
    * create generator that only generates a part of the original generator.
    *
    * @param startIndex : startIndex (inclusive)
    * @param endIndex   : endIndex (exclusive)
    * @return: generator generating the subpart of the generator as given by startIndex and endIndex
    */
  override def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[IndexedGenerator[Seq[T]]] = {
    val start = math.min(math.max(0, startIndex), nrOfElements - 1)
    val end = math.min(math.max(0, endIndex), nrOfElements)
    val batchByPart = generators(batchByIndex).getPart(start, end)
    BatchByGeneratorIndexedGenerator(generators.indices.map({
      case e if e == batchByIndex => batchByPart
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
