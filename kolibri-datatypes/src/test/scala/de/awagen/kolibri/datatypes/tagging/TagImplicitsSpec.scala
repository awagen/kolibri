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

package de.awagen.kolibri.datatypes.tagging

import de.awagen.kolibri.datatypes.tagging.Tags.{MultiTag, StringTag, Tag}
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

class TagImplicitsSpec extends UnitTestSpec {

  "TagImplicits" must {

    "correctly add toMultiTag to tag" in {
      // given
      import de.awagen.kolibri.datatypes.tagging.TagImplicits._
      val tag1: Tag = StringTag("tag1")
      val tag2: Tag = StringTag("tag2")
      val tag3: Tag = StringTag("tag3")
      val tag4: Tag = StringTag("tag4")
      // when
      val tags = tag1.toMultiTag.add(tag2).add(tag3).add(tag4)
      // then
      tags mustBe MultiTag(Set(tag1, tag2, tag3, tag4))

    }

  }

}
