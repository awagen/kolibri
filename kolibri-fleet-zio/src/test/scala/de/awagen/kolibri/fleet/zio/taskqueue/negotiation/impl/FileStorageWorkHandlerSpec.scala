/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.impl

import de.awagen.kolibri.fleet.zio.execution.JobDefinitions
import zio.test.junit.JUnitRunnableSpec
import zio.test.{Spec, TestEnvironment, assertTrue}
import zio.{Queue, Scope, ZIO}

class FileStorageWorkHandlerSpec extends JUnitRunnableSpec {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("FileStorageWorkHandlerSpec")(

    test("correctly offers multiple items") {
      // given
      val batches: Seq[JobBatch[Int, Unit]] = Range(0, 10, 1).map(batchNr => {
        JobBatch(JobDefinitions.simpleWaitJob("test", 1, 10), batchNr)
      })
      for {
        queue <- Queue.bounded[JobBatch[_, _]](5)
        workHandler <- ZIO.succeed(FileStorageWorkHandler(queue))
        offerResults <- workHandler.addBatches(batches)
      } yield assertTrue(offerResults.size == 6 && !offerResults.last && offerResults.count(e => !e) == 1)
    }

  )
}
