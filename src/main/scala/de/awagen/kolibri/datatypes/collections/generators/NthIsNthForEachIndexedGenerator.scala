package de.awagen.kolibri.datatypes.collections.generators

import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import org.slf4j.{Logger, LoggerFactory}

/**
  * IndexedGenerator that yields for index n the Seq of values made of one value per generator, while for each generator
  * its n-th element is chosen. Thus no permutations here.
  */
case class NthIsNthForEachIndexedGenerator[+T](generators: Seq[IndexedGenerator[T]]) extends IndexedGenerator[Seq[T]] {

  val logger: Logger = LoggerFactory.getLogger(NthIsNthForEachIndexedGenerator.getClass)
  val allSameSize: Boolean = generators.exists(x => x.nrOfElements != generators.head.nrOfElements)
  if (!allSameSize) {
    logger.warn("Using NthIsNthForEachIndexedGenerator with generators of different size")
  }

  override val nrOfElements: Int = generators.map(x => x.nrOfElements).min

  override def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[Seq[T]] = {
    assert(startIndex >= 0 && startIndex < nrOfElements)
    val end: Int = math.min(nrOfElements, endIndex)
    NthIsNthForEachIndexedGenerator(generators.map(x => x.getPart(startIndex, end)))
  }

  override def get(index: Int): Option[Seq[T]] = index match {
    case e if e < nrOfElements => Some(generators.map(x => x.get(index).get))
    case _ => None
  }

  override def mapGen[B](f: SerializableFunction1[Seq[T], B]): IndexedGenerator[B] = {
    new ByFunctionNrLimitedIndexedGenerator[B](nrOfElements, x => get(x).map(f))
  }
}
