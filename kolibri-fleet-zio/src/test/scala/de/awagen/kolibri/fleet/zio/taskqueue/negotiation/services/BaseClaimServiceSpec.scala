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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.TaskTopics
import de.awagen.kolibri.fleet.zio.testutils.TestObjects
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.{SnapshotSample1, claimService, fileWriterMock}
import org.mockito.Mockito.{spy, when}
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.http.Client
import zio.{Scope, ZIO, ZLayer}
import zio.test._

object BaseClaimServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("BaseClaimServiceSpec")(

    test("manageClaims") {
      // given
      val clientMock = mock[Client]
      val writerMock = fileWriterMock
      val jobStateReaderSpy = spy(TestObjects.jobStateReader(TestObjects.baseResourceFolder))
      when(jobStateReaderSpy.fetchJobState(true)).thenReturn(ZIO.succeed(SnapshotSample1.openJobsSnapshot))
      val claimH = claimService(writerMock, jobStateReaderSpy)
      // when, then
      (for {
        _ <- claimH.manageExistingClaims(TaskTopics.JobTaskProcessingTask)
      } yield assert(true)(Assertion.assertion("true")(_ => true))
        ).provide(ZLayer.succeed(clientMock))
    }
  )

}
