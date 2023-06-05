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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopic
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.{SnapshotSample1, claimService, fileWriterMock}
import zio.Scope
import zio.test._

object BaseClaimServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("BaseClaimServiceSpec")(

    test("manageClaims") {
      // given
      val writerMock = fileWriterMock
      val claimH = claimService(writerMock)
      // when, then
      for {
        _ <- claimH.manageClaims(ClaimTopic.JOB_TASK_PROCESSING_CLAIM, SnapshotSample1.openJobsSnapshot)
      } yield assert(true)(Assertion.assertion("true")(_ => true))
    }
  )

}
