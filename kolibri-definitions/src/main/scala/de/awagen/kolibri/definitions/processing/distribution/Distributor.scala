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


package de.awagen.kolibri.definitions.processing.distribution

import de.awagen.kolibri.definitions.processing.ProcessingMessages.AggregationState
import de.awagen.kolibri.definitions.processing.distribution.DistributionStates.DistributionState

trait Distributor[T, U] {

  def nrDistributed: Int

  def nrResultsAccepted: Int

  def setMaxParallelCount(count: Int): Unit

  def maxInParallel: Int

  def idsFailed: Seq[Int]

  def idsInProgress: Seq[Int]

  def unfinished: Seq[Int]

  def markAsFail(identifier: Int): Unit

  def accept(element: AggregationState[U]): Boolean

  def next: Either[DistributionState, Seq[T]]

  def hasCompleted: Boolean

}
