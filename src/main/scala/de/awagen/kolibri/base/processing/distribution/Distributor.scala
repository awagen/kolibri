package de.awagen.kolibri.base.processing.distribution

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.AggregationState
import de.awagen.kolibri.base.processing.distribution.DistributionStates.DistributionState

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

}
