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


package de.awagen.kolibri.definitions.format

import de.awagen.kolibri.definitions.testclasses.UnitTestSpec

class RegexUtilsSpec extends UnitTestSpec {

  "RegexUtils" should {

    "identify parameter in tag" in {
      // given
      val filename1: String = "(q=testquery)"
      // when
      val value: String = RegexUtils.findParamValueInString(param = "q",
        string = filename1,
        defaultValue = "MISSING_VALUE")
      // then
      value mustBe "testquery"
    }

  }

}
