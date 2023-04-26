/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format

import de.awagen.kolibri.datatypes.mutable.stores.{TypedMapStore, WeaklyTypedMap}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.FileNameFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.Parts.{BATCH_NR, TOPIC}
import de.awagen.kolibri.fleet.zio.testclasses.UnitTestSpec

class NameFormatsSpec extends UnitTestSpec {

  object TestData {
    val format1 = new FileNameFormat(Seq(TOPIC, BATCH_NR), "_")
    val testMapping1: TypedMapStore = TypedMapStore.empty
    testMapping1.put(TOPIC.namedClassTyped, "topic1")
    testMapping1.put(BATCH_NR.namedClassTyped, 1)
  }

  import TestData._

  "FileNameFormat" must {

    "correctly compose name" in {
      val name = format1.format(testMapping1)
      name mustBe "topic1_1"
    }

    "correctly decompose name" in {
      val name = "topic1_1"
      val parsed: WeaklyTypedMap[String] = format1.parse(name)
      val topicValue: String = parsed.get(TOPIC.namedClassTyped.name).get
      topicValue mustBe "topic1"
      parsed.get[Int](BATCH_NR.namedClassTyped.name).get mustBe 1
    }

  }

}
