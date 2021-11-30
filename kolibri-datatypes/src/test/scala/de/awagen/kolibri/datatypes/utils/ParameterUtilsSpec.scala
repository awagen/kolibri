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

package de.awagen.kolibri.datatypes.utils

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec


class ParameterUtilsSpec extends UnitTestSpec {

  "ExperimentUtils" must {

    "correctly translate multivalued map to url encoded query string" in {
      //given, when
      val map: Map[String, Seq[String]] = Map(
        "fq" -> Seq("{!yay tag=yay}", "flag_test:(false)"),
        "echoParams" -> Seq("none"),
        "facet" -> Seq("false")
      )
      val query = ParameterUtils.queryStringFromParameterNamesAndValues(map)
      //then
      query mustBe "fq=%7B%21yay+tag%3Dyay%7D&fq=flag_test%3A%28false%29&echoParams=none&facet=false"
    }

  }

}
