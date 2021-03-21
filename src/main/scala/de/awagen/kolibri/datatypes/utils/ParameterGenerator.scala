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

import de.awagen.kolibri.datatypes.values.OrderedValues

object ParameterGenerator {

  def indicesToValues(valueIndices: Seq[Int], values: Seq[OrderedValues[Any]]): Seq[Any] =  {
    valueIndices.indices.foreach(x => assert(valueIndices(x) < values(x).totalValueCount, s"index (${valueIndices(x)}) out of range (max index is ${values(x).totalValueCount})"))
    valueIndices.indices.foreach(x => assert(valueIndices(x) >= 0, s"index (${valueIndices(x)}) < 0"))
    valueIndices.indices.flatMap(x => values(x).getNthZeroBased(valueIndices(x))).toList
  }

  def indicesSeqToValueSeq(indicesSeq: Seq[Seq[Int]], values: Seq[OrderedValues[Any]]): Seq[Seq[Any]] = {
    indicesSeq.map(x => indicesToValues(x, values)).toList
  }

  /**
    * Sequence of parameter names corresponding to the order of parameters
    * @return
    */
  def getParameterNameSequence(values: Seq[OrderedValues[Any]]): Seq[String] = {
    values.map(x => x.name).toList
  }

  /**
    * Calculates for each parameter the total number of steps (non-combinatorial, just for this parameter)
    * @return
    */
  private[utils] def stepsPerParameter(values: Seq[OrderedValues[Any]]):Seq[Int] = {
    values.to(LazyList)
      .map(x => x.totalValueCount)
      .toList
  }

  /**
    * Returns total number of combinations of parameters
    * @return
    */
  def numberOfCombinations(values: Seq[OrderedValues[Any]]): Int = {
    stepsPerParameter(values).product
  }
}
