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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives.JobDirective
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.processing.actions.JobActions
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates
import de.awagen.kolibri.fleet.zio.testutils.TestObjects
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.{Scope, ZLayer}
import zio.http.Client
import zio.test._


object FileStorageJobStateReaderSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("FileStorageJobStateReaderSpec")(

    /**
     * Note: as of now the directory is a combination of [jobName]_[timePlacedInMillis].
     * This is what is used as keys in the mapping, not the pure
     * jobName.
     */
    test("read job state") {
      // given
      val clientMock = mock[Client]
      val reader = TestObjects.jobStateReader(TestObjects.baseResourceFolder)
      val jobKey = "testJob1_3434839787"
      // when, then
      (for {
        openJobsSnapshot <- reader.fetchJobState(true)
      } yield assert(openJobsSnapshot.jobStateSnapshots.keySet)(Assertion.equalTo(Set(jobKey))) &&
        assert(openJobsSnapshot.jobStateSnapshots(jobKey).jobId)(Assertion.equalTo(jobKey)) &&
        assert(openJobsSnapshot.jobStateSnapshots(jobKey).timePlacedInMillis)(Assertion.equalTo(3434839787L)) &&
        assert(openJobsSnapshot.jobStateSnapshots(jobKey).jobLevelDirectives)(Assertion.equalTo(Set[JobDirective](JobDirectives.Process))) &&
        assert(openJobsSnapshot.jobStateSnapshots(jobKey).actionForJob)(Assertion.equalTo(JobActions.ProcessAllNodes)) &&
        assert(openJobsSnapshot.jobStateSnapshots(jobKey).batchesToState.keySet)(Assertion.equalTo(Range(0, 10, 1).toSet)) &&
        assert(openJobsSnapshot.jobStateSnapshots(jobKey).batchesToState)(Assertion.equalTo(
          (Range(3, 10, 1).map(batchNr => (batchNr, BatchProcessingStates.Open))
            ++ Seq(
            (0, BatchProcessingStates.InProgress("abc234")),
            (1, BatchProcessingStates.InProgress("abc234")),
            (2, BatchProcessingStates.InProgress("other1"))
          )).toMap
        ))
      ).provide(ZLayer.succeed(clientMock))
    }

  )

}
