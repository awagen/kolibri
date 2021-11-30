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

class SequenceUtilsSpec extends UnitTestSpec {

  "SequenceUtils" must {
    "compare multi-type sequences taking account of their type" in {
      //given
      val seq1 = Seq(0.01f, 0.04f, "hose")
      val seq2 = Seq(0.01f, 0.0400001f, "hose")
      //when
      SequenceUtils.areSame(seq1, seq2) mustBe true
    }
  }

}
