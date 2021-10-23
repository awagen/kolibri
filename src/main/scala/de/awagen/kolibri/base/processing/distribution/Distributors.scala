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

import de.awagen.kolibri.base.actors.work.manager.JobManagerActor.Batch
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator

object Distributors {

  /**
    * Distributor distributing the single batches on nodes. Needs updates on batches considered to be failed
    * (e.g due to failing ACK or by failing the single batch expectation,...). Allows retry on failed batches.
    *
    * @param dataGen - the generator of batches
    * @param numRetries - number of retries
    * @param maxParallelTasks - maximal number of concurrently running batches
    * @return
    */
  def getRetryingDistributor[U](dataGen: IndexedGenerator[Batch], numRetries: Int, maxParallelTasks: Int): Distributor[Batch, U] = {
    new RetryingDistributor[Batch, U](
      maxParallel = maxParallelTasks,
      generator = dataGen,
      maxNrRetries = numRetries)
  }

}
