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

import de.awagen.kolibri.base.domain.jobdefinitions.provider.QueryAndParamProvider
import de.awagen.kolibri.base.io.json.QueryAndParamProviderJsonProtocol._
import de.awagen.kolibri.base.io.reader.LocalResourceFileReader
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.multivalues.GridOrderedMultiValues
import de.awagen.kolibri.datatypes.values.DistinctValues
import spray.json._


class QueryAndParamProviderJsonProtocolSpec extends UnitTestSpec {

  "QueryAndParamProviderJsonProtocol" must {

    "parse QueryAndParamProvider" in {
      // given
      val json = """{"contextPath": "testPath", "queryFile": "data/queryterms.txt", "queryFileReader": {"type": "LOCAL_FILE_READER", "delimiter": "/", "position": 1, "encoding": "UTF-8"}, "parameters": {"values": [{"name": "test", "values": [0.45, 0.32]}]}, "defaultParameters": {"p1": ["v1_1", "v1_2"]}}""".parseJson
      val expectedProvider = QueryAndParamProvider(
        contextPath = "testPath",
        queryFile = "data/queryterms.txt",
        queryFileReader = LocalResourceFileReader(
          delimiterAndPosition = Some(("/", 1))
        ),
        parameters = GridOrderedMultiValues(
          values = Seq(DistinctValues("test", Seq(0.45, 0.32)))
        ),
        defaultParameters = Map("p1" -> Seq("v1_1", "v1_2"))
      )
      // when
      val provider: QueryAndParamProvider = json.convertTo[QueryAndParamProvider]
      // then
      provider mustBe expectedProvider
    }

  }

}
