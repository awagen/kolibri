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

package de.awagen.kolibri.datatypes.multivalues

import de.awagen.kolibri.datatypes.values.OrderedValues

/**
 * Batch representing at most batchSize elements of its input values. Starts at position batchSize * (batchNr -1) + 1
 * of the original values.
 *
 * @param multiValues The original values this batch represents a part of
 * @param batchSize   The maximum size of this batch (might contain less for last batch of values)
 * @param batchNr     The number of this batch. Used to determine which part of the values this batch represents. 1-based
 */
case class GridOrderedMultiValuesBatch(multiValues: OrderedMultiValues, batchSize: Int, batchNr: Int) extends OrderedMultiValuesBatch {

  val values: Seq[OrderedValues[Any]] = multiValues.values

  val batchStartElement: Int = batchSize * (batchNr - 1)
  val batchEndElement: Int = this.batchStartElement + batchSize //exclusive

  override def getParameterNameSequence: Seq[String] = {
    this.multiValues.getParameterNameSequence
  }

  override def numberOfCombinations: Int = {
    val experimentTotalElements: Int = this.multiValues.numberOfCombinations
    Math.min(batchSize, Math.max(0, experimentTotalElements - batchStartElement))
  }

  /**
   * Find nrOfElements next elements for this batch starting from element startElement.
   * Uses the implementation of the underlying experiment to find the respective elements.
   *
   * @param startElement 0-based, e.g first element corresponds to startElement = 0
   * @param nrOfElements Total number of elements to pick
   * @return
   */
  override def findNNextElementsFromPosition(startElement: Int, nrOfElements: Int): Seq[Seq[Any]] = {
    val batchStartElement = this.batchStartElement + startElement
    val pickNrOfElements = Math.min(batchEndElement - batchStartElement, nrOfElements)

    if (pickNrOfElements <= 0) return List.empty
    this.multiValues.findNNextElementsFromPosition(batchStartElement, pickNrOfElements)
  }

  def findNthElement(n: Int): Option[Seq[Any]] = {
    if (n >= batchSize || n < 0) return Option.empty
    val experimentNth: Int = n + batchStartElement
    this.multiValues.findNthElement(experimentNth)
  }

  override def stepsForNthElementStartingFromFirstParam(n: Int): List[(Int, Int)] = {
    assert(n < batchSize && n >= 0, s"Batch element number must be > 0 and smaller batchSize ($batchSize), but is $n")
    multiValues.stepsForNthElementStartingFromFirstParam(batchStartElement + n)
  }

  override def addValue(values: OrderedValues[Any], prepend: Boolean): OrderedMultiValues = {
    throw new UnsupportedOperationException("Changing the OrderedValues not supported in batch")
  }

  override def addValues(values: Seq[OrderedValues[Any]], prepend: Boolean): OrderedMultiValues = {
    throw new UnsupportedOperationException("Changing the OrderedValues not supported in batch")
  }

  override def addValues(values: OrderedMultiValues, prepend: Boolean): OrderedMultiValues = {
    throw new UnsupportedOperationException("Changing the OrderedValues not supported in batch")
  }

  override def removeValue(valueName: String): (OrderedMultiValues, Boolean) = {
    throw new UnsupportedOperationException("Changing the OrderedValues not supported in batch")
  }
}
