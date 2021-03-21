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

package de.awagen.kolibri.base.http.client.domain

import de.awagen.kolibri.base.testclasses.UnitTestSpec

class ParameterSpec extends UnitTestSpec {

  "Parameter" must {

    "compose parameter list as query string" in {
      // given
      val params = Seq(Parameter("p1", "v1_1"), Parameter("p1", "v1_2"), Parameter("p2", "v2_1"))
      // when
      val paramString: String = Parameter.listAsQueryString(params)
      // then
      paramString mustBe "p1=v1_1&p1=v1_2&p2=v2_1"
    }

    "compose parameter list as map" in {
      // given
      val params = Seq(Parameter("p1", "v1_1"), Parameter("p1", "v1_2"), Parameter("p2", "v2_1"))
      // when
      val paramMap: Map[String, Seq[String]] = Parameter.listAsParameterMap(params)
      // then
      paramMap mustBe Map("p1" -> Seq("v1_1", "v1_2"), "p2" -> Seq("v2_1"))
    }

  }

}
