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

package de.awagen.kolibri.base.processing.distribution

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.AggregationState
import de.awagen.kolibri.base.traits.Traits.WithBatchNr
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import org.slf4j.{Logger, LoggerFactory}


/**
  * Distributor that distributes once and only accepts leftover results once
  * TODO: need override of next to filter out batches that are not contained
  * in acceptOnlyIds anyways
  */
class FilteringOnceDistributor[T <: WithBatchNr, U](private[this] var maxParallel: Int,
                                                    generator: IndexedGenerator[T],
                                                    private[this] var acceptOnlyIds: Set[Int])
  extends ProcessOnceDistributor[T, U](
    maxParallel,
    generator) {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def accept(element: AggregationState[U]): Boolean = {
    logger.debug(s"distributor: received aggregation state: $element")
    var didAccept: Boolean = false
    if (acceptOnlyIds.contains(element.batchNr)) {
      acceptOnlyIds = acceptOnlyIds - element.batchNr
      didAccept = true
    }
    else {
      logger.warn(s"received result with ignored id '${element.batchNr}'")
    }
    removeBatchRecords(element.batchNr)
    didAccept
  }

}
