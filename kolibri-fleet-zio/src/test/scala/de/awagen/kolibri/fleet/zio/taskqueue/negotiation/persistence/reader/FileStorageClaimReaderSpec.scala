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

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopics
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ClaimStates._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.claimReader
import zio.Scope
import zio.test._

object FileStorageClaimReaderSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("FileStorageClaimReaderSpec")(

    test("read all claims for job") {
      // given
      val reader = claimReader
      val expectedClaimSet = Set(
        Claim("testJob1_3434839787", 2, "abc234", 1683845333850L, ClaimTopics.JobTaskProcessingClaim),
        Claim("testJob1_3434839787", 2, "other1", 1703845333850L, ClaimTopics.JobTaskProcessingClaim),
        Claim("testJob1_3434839787", 2, "other2", 1713845333850L, ClaimTopics.JobTaskProcessingClaim),
        Claim("testJob1_3434839787", 3, "other1", 1683845333850L, ClaimTopics.JobTaskProcessingClaim)
      )
      // when, then
      for {
        claims <- reader.getAllClaims(Set("testJob1_3434839787"), ClaimTopics.JobTaskProcessingClaim)
      } yield assert(claims)(Assertion.assertion("reads all claims")(entries => {
        entries.equals(expectedClaimSet)
      }))
    },

    test("verifyBatchClaim") {
      val reader = claimReader
      for {
        claimResult1 <- reader.verifyBatchClaim("testJob1_3434839787", 2, ClaimTopics.JobTaskProcessingClaim)
        claimResult2 <- reader.verifyBatchClaim("testJob1_3434839787", 3, ClaimTopics.JobTaskProcessingClaim)
      } yield assert(claimResult1)(Assertion.equalTo(ClaimVerifyStatus.CLAIM_ACCEPTED)) &&
        assert(claimResult2)(Assertion.equalTo(ClaimVerifyStatus.NODE_CLAIM_DOES_NOT_EXIST))
    }

  )

}
