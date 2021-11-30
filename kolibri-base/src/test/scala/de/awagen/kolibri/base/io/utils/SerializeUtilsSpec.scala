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

package de.awagen.kolibri.base.io.utils

import de.awagen.kolibri.base.testclasses.UnitTestSpec
import spray.json._

class SerializeUtilsSpec extends UnitTestSpec {

  "SerializeUtils" must {

    "serialize and deserialize" in {
      // given
      val json = """{"key1": "val1", "complex1": {"ck1": "ckv1"}}""".parseJson
      // when
      val serialized: Array[Byte] = SerializeUtils.serialize(json)
      val deserialized = SerializeUtils.deserialize(serialized)
      // then
      deserialized mustBe json
    }

  }

}
