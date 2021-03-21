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

package de.awagen.kolibri.datatypes.io.json

import de.awagen.kolibri.datatypes.fixtures.ValuesFixtures
import de.awagen.kolibri.datatypes.io.ValuesIO
import de.awagen.kolibri.datatypes.multivalues.GridOrderedMultiValues
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.values.OrderedValues


class ValuesIOSpec extends UnitTestSpec {

  "An JsonIO must provide methods to" must {
    "correctly parse experiment json" in {
      val experimentObj: GridOrderedMultiValues = ValuesIO.jsValueToGridOrderedMultiValues(ValuesFixtures.experiment)
      experimentObj.values.size mustBe 2
    }
    "throw NoSuchElementException if json does not reflect object Read structure" in {
      a[NoSuchElementException] must be thrownBy {
        val experimentParam: OrderedValues[Any] = ValuesIO.jsValueToOrderedValues(ValuesFixtures.experiment)
      }
    }
  }

}
