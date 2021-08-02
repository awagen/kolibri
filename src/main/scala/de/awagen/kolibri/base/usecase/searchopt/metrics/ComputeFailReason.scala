package de.awagen.kolibri.base.usecase.searchopt.metrics

import de.awagen.kolibri.datatypes.reason.ComputeFailReason

object ComputeFailReason {

  def missingKeyFailReason(key: String): ComputeFailReason = de.awagen.kolibri.datatypes.reason.ComputeFailReason(s"MISSING_DATA_KEY-$key")

}
