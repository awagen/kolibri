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

import de.awagen.kolibri.datatypes.collections.generators.NthIsNthForEachIndexedGenerator.logger
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import org.slf4j.{Logger, LoggerFactory}

object NthIsNthForEachIndexedGenerator {

  private val logger: Logger = LoggerFactory.getLogger(NthIsNthForEachIndexedGenerator.getClass)

}

/**
  * IndexedGenerator that yields for index n the Seq of values made of one value per generator, while for each generator
  * its n-th element is chosen. Thus no permutations here.
  */
case class NthIsNthForEachIndexedGenerator[+T](generators: Seq[IndexedGenerator[T]]) extends IndexedGenerator[Seq[T]] {

  def filterFunc[B >: T]: SerializableFunction1[IndexedGenerator[B], Boolean] = new SerializableFunction1[IndexedGenerator[B], Boolean] {
    override def apply(v1: IndexedGenerator[B]): Boolean = {
      v1.nrOfElements != generators.head.nrOfElements
    }
  }
  val allSameSize: Boolean = generators.exists(filterFunc)
  if (!allSameSize) {
    logger.warn("Using NthIsNthForEachIndexedGenerator with generators of different size")
  }

  def sizeFunc[B >: T]: SerializableFunction1[IndexedGenerator[B], Int] = new SerializableFunction1[IndexedGenerator[B], Int] {
    override def apply(v1: IndexedGenerator[B]): Int = v1.nrOfElements
  }
  override val nrOfElements: Int = generators.map(sizeFunc).min

  override def getPart(startIndex: Int, endIndex: Int): IndexedGenerator[Seq[T]] = {
    assert(startIndex >= 0 && startIndex < nrOfElements)
    val end: Int = math.min(nrOfElements, endIndex)
    def getPartFunc[B >: T]: SerializableFunction1[IndexedGenerator[B], IndexedGenerator[B]] = new SerializableFunction1[IndexedGenerator[B], IndexedGenerator[B]] {
      override def apply(v1: IndexedGenerator[B]): IndexedGenerator[B] = {
        v1.getPart(startIndex, end)
      }
    }
    NthIsNthForEachIndexedGenerator(generators.map(getPartFunc))
  }

  override def get(index: Int): Option[Seq[T]] = {
    def getFunc[B >: T]: SerializableFunction1[IndexedGenerator[B], B] = new SerializableFunction1[IndexedGenerator[B], B] {
      override def apply(v1: IndexedGenerator[B]): B = v1.get(index).get
    }
    index match {
      case e if e < nrOfElements =>
        Some(generators.map(getFunc))
      case _ => None
    }
  }

  override def mapGen[B](f: SerializableFunction1[Seq[T], B]): IndexedGenerator[B] = {
    val getMapFunc: SerializableFunction1[Int, Option[B]] = new SerializableFunction1[Int, Option[B]] {
      override def apply(v1: Int): Option[B] = get(v1).map(f)
    }
    new ByFunctionNrLimitedIndexedGenerator[B](nrOfElements, getMapFunc)
  }
}
