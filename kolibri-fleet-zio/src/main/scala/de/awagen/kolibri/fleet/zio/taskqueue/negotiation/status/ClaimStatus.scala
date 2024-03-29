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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status

object ClaimStatus {

  object ClaimFilingStatus extends Enumeration {
    type ClaimFilingStatus = super.Value

    val OTHER_CLAIM_EXISTS, PERSIST_FAIL, PERSIST_SUCCESS = super.Value
  }

  object ClaimVerifyStatus extends Enumeration {
    type ClaimVerifyStatus = super.Value

    val NO_CLAIM_EXISTS, NODE_CLAIM_DOES_NOT_EXIST, CLAIM_ACCEPTED, OTHER_CLAIMED_EARLIER, FAILED_VERIFICATION = super.Value
  }

  object ClaimExerciseStatus extends Enumeration {
    type ClaimExerciseStatus = super.Value

    val EXERCISE_SUCCESS, FAILED_STATUS_UPDATE = super.Value
  }

}
