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


package de.awagen.kolibri.base.usecase.searchopt.metrics

import de.awagen.kolibri.base.testclasses.UnitTestSpec

class MissingValueFunctionsSpec extends UnitTestSpec {

  object Fixtures {

    val data: Seq[Option[Double]] = Seq(Some(0.2), None, Some(0.3), None, Some(0.4))

  }

  "MissingValueFunctions" must {

    "treat missing as avg of non-missing" in {
      // given, when
      val result: Seq[Double] = MissingValueFunctions.treatMissingAsAvgOfNonMissing(Fixtures.data)
      // then
      result mustBe Seq(0.2, 0.3, 0.3, 0.3, 0.4)
    }

    "treat missing as constant" in {
      // given, when
      val result: Seq[Double] = MissingValueFunctions.treatMissingAsConstant(0.5).apply(Fixtures.data)
      // then
      result mustBe Seq(0.2, 0.5, 0.3, 0.5, 0.4)
    }

  }

}
