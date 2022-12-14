/**
 * Copyright 2022 Andreas Wagenmann
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


package de.awagen.kolibri.datatypes.io.json

import de.awagen.kolibri.datatypes.io.json.ComputeFailReasonJsonProtocol.ComputeFailReasonFormat
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import spray.json._

class ComputeFailReasonJsonProtocolSpec extends UnitTestSpec {

  "ComputeFailReasonJsonProtocol" must {

    "correctly read ComputeFailReason" in {
      // given
      val noResultsStr = "NO_RESULTS"
      val zeroDenominatorStr = "ZERO_DENOMINATOR"
      val failedHistogramStr = "FAILED_HISTOGRAM"
      // when, then
      JsString(noResultsStr).convertTo[ComputeFailReason] mustBe ComputeFailReason.NO_RESULTS
      JsString(zeroDenominatorStr).convertTo[ComputeFailReason] mustBe ComputeFailReason.ZERO_DENOMINATOR
      JsString(failedHistogramStr).convertTo[ComputeFailReason] mustBe ComputeFailReason.FAILED_HISTOGRAM
    }

  }

}
