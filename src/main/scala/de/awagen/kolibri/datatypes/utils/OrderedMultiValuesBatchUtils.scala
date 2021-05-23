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

package de.awagen.kolibri.datatypes.utils

import de.awagen.kolibri.datatypes.collections.generators.ByFunctionNrLimitedIndexedGenerator
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValuesBatch, OrderedMultiValues, OrderedMultiValuesBatch}
import de.awagen.kolibri.datatypes.values.{DistinctValues, OrderedValues}

object OrderedMultiValuesBatchUtils {

  def splitIntoBatchByParameter(multiValues: OrderedMultiValues, byParameterName: String): Iterator[OrderedMultiValues] = {
    new Iterator[OrderedMultiValues] {

      assert(multiValues.getParameterNameSequence.contains(byParameterName))
      val batchByParamOption: Option[OrderedValues[_]] = multiValues.values.find(x => x.name.eq(byParameterName))
      val batchByParam: OrderedValues[Any] = batchByParamOption.get
      val batchParamIterator: Iterator[Any] = ByFunctionNrLimitedIndexedGenerator[Any](nrOfElements = batchByParam.totalValueCount,
        genFunc = index => batchByParam.getNthZeroBased(index)).iterator

      override def hasNext: Boolean = batchParamIterator.hasNext

      override def next(): OrderedMultiValues = {
        multiValues.removeValue(byParameterName)._1.addValue(
          DistinctValues(byParameterName, Seq(batchParamIterator.next())), prepend = true)
      }
    }
  }


  /**
    * Splitting current full experiment into iterator of batches of defined size (at most, since the last batch might be smaller)
    *
    * @param batchSize
    * @return
    */
  def splitIntoBatchIteratorOfSize(multiValues: OrderedMultiValues, batchSize: Int): Iterator[OrderedMultiValuesBatch] = {

    new Iterator[OrderedMultiValuesBatch] {
      val totalSteps: Int = multiValues.numberOfCombinations
      val modFraction: Float = (totalSteps % batchSize).toFloat / batchSize
      val totalBatches: Int = if (modFraction > 0) totalSteps / batchSize + 1 else totalSteps / batchSize

      var currentBatch = 0

      override def hasNext: Boolean = currentBatch < totalBatches

      override def next(): OrderedMultiValuesBatch = {
        currentBatch += 1
        GridOrderedMultiValuesBatch(multiValues, batchSize, currentBatch)
      }
    }
  }

  /**
    * Splitting current full experiment into batches of defined size (at most, since the last batch might be smaller)
    *
    * @param batchSize
    * @return
    */
  def splitIntoBatchesOfSize(multiValues: OrderedMultiValues, batchSize: Int): Seq[OrderedMultiValuesBatch] = {
    splitIntoBatchIteratorOfSize(multiValues, batchSize).toSeq
  }

  def splitIntoBatchIteratorAtMostNrOfBatches(multiValues: OrderedMultiValues, nrOfBatches: Int): Iterator[OrderedMultiValuesBatch] = {
    var useNrOfBatches = nrOfBatches
    val totalSteps: Int = multiValues.numberOfCombinations
    if (useNrOfBatches > totalSteps) useNrOfBatches = totalSteps
    var binSize: Int = totalSteps / useNrOfBatches
    while (binSize * useNrOfBatches < totalSteps) {
      binSize += 1
    }
    while (binSize * (useNrOfBatches - 1) >= totalSteps && useNrOfBatches > 1) {
      useNrOfBatches -= 1
    }
    var currentBatch = 0
    new Iterator[OrderedMultiValuesBatch]() {
      override def hasNext: Boolean = currentBatch < useNrOfBatches

      override def next(): OrderedMultiValuesBatch = {
        currentBatch += 1
        GridOrderedMultiValuesBatch(multiValues, binSize, currentBatch)
      }
    }
  }

  def splitIntoAtMostNrOfBatches(multiValues: OrderedMultiValues, nrOfBatches: Int): Seq[OrderedMultiValuesBatch] = {
    splitIntoBatchIteratorAtMostNrOfBatches(multiValues: OrderedMultiValues, nrOfBatches: Int).toSeq
  }

  /**
    * Find the sequence of combinations corresponding to a specific batch
    *
    * @param batchSize The size of one batch
    * @param batchNr   The number of the current batch (1-based)
    * @return
    */
  def findBatch(multiValues: OrderedMultiValues, batchSize: Int, batchNr: Int): Seq[Seq[Any]] = {
    assert(batchSize > 0, s"Batch size needs to be greater than zero, but is $batchSize")
    assert(batchNr > 0, s"Batch number needs to be greater than zero, but is $batchNr")
    val startElementNr: Int = batchSize * (batchNr - 1)
    multiValues.findNNextElementsFromPosition(startElementNr, batchSize)
  }

}
