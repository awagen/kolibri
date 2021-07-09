package de.awagen.kolibri.base.processing.distribution

object DistributionStates {

  trait DistributionState

  // means right now we dont have a batch to distribute,
  // but there are still some to distribute (e.g waiting for results)
  case object Pausing extends DistributionState

  // means all is distributed (including retries)
  case object Completed extends DistributionState

  case object AllProvidedWaitingForResults extends DistributionState

}
