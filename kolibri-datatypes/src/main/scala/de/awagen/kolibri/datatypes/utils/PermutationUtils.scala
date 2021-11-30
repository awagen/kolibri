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

import scala.collection.mutable.ListBuffer

/**
  * Utility object providing methods for calculating permutations on ordered values. The implementation here is purely based on
  * indices (0-based), e.g given a sequence providing the number of elements for each parameter (ith value in sequence gives
  * number of elements of ith parameter). Thus to calculate the corresponding values, each index i must be translated to the
  * i-th value of the respective parameter
  *
  * The methods here are provided to be able to avoid calculating crossproducts, which can be very expensive memory-wise,
  * and be able to calculate permutations by directly returning permutation n, or m values starting from position n.
  */
object PermutationUtils {

  /**
    * Zero-based. Given a seq of current indices and a seq of nr of elements
    * (where indices in the first correspond to indices in the latter, e.g both must be of same length)
    * find the first that is not yet at its max value
    *
    * @param currentIndices           - the current indices for each position
    * @param nrOfElementsPerParameter - the total nr of elements for each position
    * @return Option[Int]. None if all are at max, or Some(elementIndex) if elementIndex is not at its max
    */
  private[utils] def findFirstNonMaxIndex(currentIndices: Seq[Int], nrOfElementsPerParameter: Seq[Int]): Option[Int] = {
    currentIndices.indices.to(LazyList)
      .find(x => currentIndices(x) < nrOfElementsPerParameter(x) - 1)
  }

  /**
    * Same as iterateFirstTillMax(currentPosition: Seq[Int], nrOfValuesPerParameter: Seq[Int])
    * but limiting the number of elements to nrOfElements
    *
    * @param currentPosition
    * @param nrOfValuesPerParameter
    * @param nrOfElements
    * @return
    */
  private[utils] def iterateFirstTillMax(currentPosition: Seq[Int], nrOfValuesPerParameter: Seq[Int], nrOfElements: Int): Seq[Seq[Int]] = {
    val indexSequences: Seq[Seq[Int]] = iterateFirstTillMax(currentPosition, nrOfValuesPerParameter)
    indexSequences.slice(0, math.min(indexSequences.size, nrOfElements))
  }

  /**
    * Takes all values of index for the first parameter (currentPosition till max value)
    * and for each value appends the unchanged indices for the later elements.
    * NOTE: result does not include the current position
    *
    * @param currentPosition
    * @param nrOfValuesPerParameter
    * @return Seq[Seq[Int]]: seq of indices-per-element sequence with all but index for first element
    *         unchanged
    */
  private[utils] def iterateFirstTillMax(currentPosition: Seq[Int], nrOfValuesPerParameter: Seq[Int]): Seq[Seq[Int]] = {
    val range = currentPosition.head until nrOfValuesPerParameter.head
    range.slice(1, range.size)
      .map(x => x +: currentPosition.slice(1, currentPosition.size))
      .toList
  }

  /**
    * Increase parameter at position defined by index (0-based) by one step and reset the parameters defined by smaller
    * index. Leave the others unchanged
    *
    * @param seq   The start sequence
    * @param index The index (0-based), identifying the parameter
    * @return The newly generated sequence
    */
  private[utils] def increaseByStepAndResetPrevious(seq: Seq[Int], index: Int): Seq[Int] = {
    seq.indices.to(LazyList).map {
      case i if i < index => 0
      case i if i == index => seq(i) + 1
      case i => seq(i)
    }.toList
  }

  /**
    * Find the first nr elements for the set parameters, starting from start values
    *
    * @param nr The nr of elements
    * @return The first n elements (at most of size nr)
    */
  def generateFirstParameterIndices(nr: Int, nrOfValuesPerParameter: Seq[Int]): Seq[Seq[Int]] = {
    val startValue: Seq[Int] = nrOfValuesPerParameter.to(LazyList).map(x => 0).toList
    startValue +: generateNextParameters(startValue, nrOfValuesPerParameter, nr - 1)
  }


  /**
    * Given a current index sequence, generate the next nr of index sequences, each giving the current element per parameter.
    * Quite robust even for large nr of elements due to iterative procedure
    *
    * @param seq The starting sequence
    * @param nr  The nr of next sequences (where the value n at ith position indicates that parameter i should be set to its nth value)
    *            to generate
    * @return The n next index sequences starting from seq (at most nr elements). Does not include the starting sequence
    */
  def generateNextParameters(seq: Seq[Int], nrOfValuesPerParameter: Seq[Int], nr: Int): Seq[Seq[Int]] = {
    if (nr <= 0) return List.empty

    val addThese: ListBuffer[Seq[Int]] = ListBuffer()
    var currentStart = seq
    var break: Boolean = false

    while (addThese.size < nr && !break) {
      val firstNonMax = findFirstNonMaxIndex(currentStart, nrOfValuesPerParameter)
      firstNonMax match {
        case Some(0) =>
          addThese appendAll iterateFirstTillMax(currentStart, nrOfValuesPerParameter, nr - addThese.size)
          currentStart = addThese.last
        case Some(e) =>
          val startWith: Seq[Int] = increaseByStepAndResetPrevious(currentStart, e)
          addThese append startWith
          if (nr - addThese.size > 0) {
            addThese appendAll iterateFirstTillMax(startWith, nrOfValuesPerParameter, nr - addThese.size)
          }
          currentStart = addThese.last
        case None => break = true
      }
    }
    addThese.result()
  }

  /**
    * Given a position (0-based) of all the combinations, returns n next elements (at most nrOfElements elements),
    * including the startElementNr-th element. Provides the index sequence for each parameter. To get the actual values
    * those positions have to be requested from the values, e.g for ith index with value n in the result,
    * wed get the actual values by values(i).getNthZeroBased(n)
    *
    * @param startElementNr
    * @param nrOfElements
    * @return
    */
  def findNNextElementsFromPosition(nrOfValuesPerParameter: Seq[Int], startElementNr: Int, nrOfElements: Int): Seq[Seq[Int]] = {
    if (nrOfElements == 0) return List.empty
    val startElement: Option[Seq[Int]] = findNthElementForwardCalc(nrOfValuesPerParameter, startElementNr)
    if (nrOfElements == 1) return if (startElement.nonEmpty) List(startElement.get) else List.empty
    startElement match {
      case Some(el) => el +: generateNextParameters(el, nrOfValuesPerParameter, nrOfElements - 1)
      case None => List.empty
    }
  }

  /**
    * Returns n-th element (0-based) of parameter sequence. None if no such element existing.
    * Procedure: start from first element, increase till max, then go to next element, increase that
    * and reset all element with smaller indices to first value (0) and continue like that
    *
    * @param n Position of the requested element (0-based)
    * @return
    */
  def findNthElementForwardCalc(nrOfValuesPerParameter: Seq[Int], n: Int): Option[Seq[Int]] = {
    if (n > nrOfValuesPerParameter.product - 1 || n < 0) return Option.empty

    var currentValue = nrOfValuesPerParameter.map(_ => 0) //for all params, set starting value
    var currentPosition: Int = 0
    var break: Boolean = false

    while (currentPosition < n && !break) {
      val firstNonMax = findFirstNonMaxIndex(currentValue, nrOfValuesPerParameter)
      firstNonMax match {
        case Some(0) =>
          val nextNumSteps = math.min(nrOfValuesPerParameter.head - 1 - currentValue.head, n - currentPosition)
          currentPosition += nextNumSteps
          currentValue = (currentValue.head + nextNumSteps) +: currentValue.slice(1, currentValue.size)
        case Some(e) =>
          currentValue = increaseByStepAndResetPrevious(currentValue, e)
          currentPosition += 1
          if (currentPosition < n && nrOfValuesPerParameter.head > 1) {
            val nextNumSteps = math.min(nrOfValuesPerParameter.head - 1 - currentValue.head, n - currentPosition)
            currentPosition += nextNumSteps
            currentValue = (currentValue.head + nextNumSteps) +: currentValue.slice(1, currentValue.size)
          }
        case None => break = true
      }
    }
    if (currentPosition == n) Some(currentValue) else None
  }

  /**
    * NOTE: this provides a list of tuples where x._1 is the parameter index and x._2 is the
    * element index for the given parameter. It just contains the relevant steps,
    * e.g the first m parameters needed to satisfy the dependency. The indices for the remaining
    * parameters ones would just need to be set to 0
    *
    * @param n number of element
    * @return List of tuples, where first element corresponds to index and second to the respective step
    *         (step is 0-based).
    */
  def stepsForNthElementBackwardCalc(stepsPerParam: Seq[Int], n: Int): List[(Int, Int)] = {
    assert(stepsPerParam.product >= n, s"Number of combinations (${stepsPerParam.product}) smaller than requested element number ($n)")

    // find first index for which the total nr of possibly permutations is >= n
    val index = stepsPerParam.indices
      .find(x => stepsPerParam.slice(0, x + 1).product >= (n + 1)).get

    // excluding the determined index, calc how many permutations would be generated by
    // all combinations of indices < determined index (e.g in case of index = 0 this would be 1)
    val startCombinations = stepsPerParam.slice(0, index).product

    // calculate first pair of index of element for the current index for which total number
    // of permutations >= needed permutations. Afterwards we will just remove the leftover from the
    // max of permutations from the previous indices and start the calculation anew
    val (indexForParam, combinationsCount): (Int, Int) = (0 until stepsPerParam(index)).to(LazyList)
      .map(x => (x, startCombinations * (x + 1)))
      .find(x => x._2 >= (n + 1)).get

    //if all combinations for current setting of current index are too many, find the setting for the previous values
    //where the too many elements are substracted from the number of their total combinations
    // removeFromMaxPermutationsOfRemainingIndices will always be <= 0
    val removeFromMaxPermutationsOfRemainingIndices = n - combinationsCount
    // now limit search to the previous indices and find the nthElementOfPreviousIndices element
    // to find the right position
    val nthElementOfPreviousIndices: Int = startCombinations + removeFromMaxPermutationsOfRemainingIndices

    if (index == 0) (index, indexForParam) :: Nil
    else stepsForNthElementBackwardCalc(stepsPerParam, nthElementOfPreviousIndices) ::: ((index, indexForParam) :: Nil)
  }

}
