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


package de.awagen.kolibri.definitions.utils

import de.awagen.kolibri.definitions.testclasses.UnitTestSpec

class IterableUtilsSpec extends UnitTestSpec {

  "IterableUtils" must {

    "correctly combine maps" in {
      // given
      val map1 = Map("k1" -> Seq("v1", "v2"), "k2" -> Seq("z1"))
      val map2 = Map("k1" -> Seq("m1"), "k2" -> Seq("l1"), "k3" -> Seq("a1"))
      // when
      val resultWithoutReplace = IterableUtils.combineMaps(map1, map2, replace = false)
      val resultWithReplace = IterableUtils.combineMaps(map1, map2, replace = true)
      // then
      resultWithoutReplace mustBe Map(
        "k1" -> Seq("v1", "v2", "m1"),
        "k2" -> Seq("z1", "l1"),
        "k3" -> Seq("a1"))
      resultWithReplace mustBe Map(
        "k1" -> Seq("m1"),
        "k2" -> Seq("l1"),
        "k3" -> Seq("a1"))
    }

  }

}
