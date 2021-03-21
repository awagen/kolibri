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

package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.http.client.request.RequestContextProvider
import de.awagen.kolibri.base.io.json.RequestContextProviderJsonProtocol._
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.multivalues.GridOrderedMultiValues
import de.awagen.kolibri.datatypes.values.DistinctValues
import spray.json._

class RequestContextProviderJsonProtocolSpec extends UnitTestSpec {

  "RequestContextProviderJsonProtocol" must {

    "parse RequestContextProvider" in {
      // given
      val json =
        """{"groupId": "testId", "contextPath": "testPath", "fixedParams": {"p1": ["v_1", "v_2"]}, "variedParams": {"values": [{"name": "test", "values": [0.45, 0.32]}]}}""".parseJson
      val expectedProvider = RequestContextProvider(
        groupId = "testId",
        contextPath = "testPath",
        fixedParams = Map("p1" -> Seq("v_1", "v_2")),
        variedParams = GridOrderedMultiValues(
          values = Seq(DistinctValues("test", Seq(0.45, 0.32)))
        )
      )
      // when
      val result = json.convertTo[RequestContextProvider]
      // then
      result mustBe expectedProvider

    }

  }

}
