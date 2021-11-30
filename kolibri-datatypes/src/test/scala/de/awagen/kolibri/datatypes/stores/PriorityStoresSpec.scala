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


package de.awagen.kolibri.datatypes.stores

import de.awagen.kolibri.datatypes.stores.PriorityStores.BasePriorityStore
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

class PriorityStoresSpec extends UnitTestSpec {

  "BasePriorityStore" must {

    "keep right prios for bigger than comparison" in {
      // given
      val intPrioStore = BasePriorityStore[String, Int](5, (x, y) => if (x > y) 1 else -1, x => {
        if (x % 2 == 0) "even" else "uneven"
      })
      // when
      Range(0, 100).foreach(intPrioStore.addEntry)
      // then
      intPrioStore.result mustBe Map(
        "even" -> Seq(98, 96, 94, 92, 90),
        "uneven" -> Seq(99, 97, 95, 93, 91)
      )
    }

    "keep right prios for smaller than comparison" in {
      // given
      val intPrioStore = BasePriorityStore[String, Int](5, (x, y) => if (x > y) -1 else 1, x => {
        if (x % 2 == 0) "even" else "uneven"
      })
      // when
      Range(0, 100).reverse.foreach(intPrioStore.addEntry)
      // then
      intPrioStore.result mustBe Map(
        "even" -> Seq(0, 2, 4, 6, 8),
        "uneven" -> Seq(1, 3, 5, 7, 9)
      )
    }

  }

}
