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

import de.awagen.kolibri.datatypes.tagging.TagType.{AGGREGATION, DESCRIPTION}
import de.awagen.kolibri.datatypes.tagging.Tags.{NamedTag, StringTag}
import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

class TaggedWithTypeSpec extends UnitTestSpec {

  case class TestTagged() extends TaggedWithType

  "TaggedWithType" must {

    "correctly add non-duplicate tags for named tagged" in {
      // given
      val testTagged = TestTagged()
      // when
      testTagged.addTag(AGGREGATION, NamedTag("name1", StringTag("s1")))
      testTagged.addTag(AGGREGATION, NamedTag("name1", StringTag("s1")))
      testTagged.addTag(AGGREGATION, NamedTag("name2", StringTag("s2")))
      testTagged.addTag(DESCRIPTION, NamedTag("name3", StringTag("s3")))
      testTagged.addTag(DESCRIPTION, NamedTag("name4", StringTag("s4")))
      testTagged.addTag(DESCRIPTION, NamedTag("name4", StringTag("s4")))
      // then
      testTagged.getTagsForType(AGGREGATION).size mustBe 2
      testTagged.getTagsForType(AGGREGATION) mustBe Set(NamedTag("name1", StringTag("s1")),
        NamedTag("name2", StringTag("s2")))
      testTagged.getTagsForType(DESCRIPTION).size mustBe 2
      testTagged.getTagsForType(DESCRIPTION) mustBe Set(NamedTag("name3", StringTag("s3")),
        NamedTag("name4", StringTag("s4")))
    }

    "correctly add non-duplicate tags for string tagged" in {
      // given
      val testTagged = TestTagged()
      // when
      testTagged.addTag(AGGREGATION, StringTag("s1"))
      testTagged.addTag(AGGREGATION, StringTag("s1"))
      testTagged.addTag(AGGREGATION, StringTag("s2"))
      testTagged.addTag(DESCRIPTION, StringTag("s3"))
      testTagged.addTag(DESCRIPTION, StringTag("s4"))
      testTagged.addTag(DESCRIPTION, StringTag("s4"))
      // then
      testTagged.getTagsForType(AGGREGATION).size mustBe 2
      testTagged.getTagsForType(AGGREGATION) mustBe Set(StringTag("s1"), StringTag("s2"))
      testTagged.getTagsForType(DESCRIPTION).size mustBe 2
      testTagged.getTagsForType(DESCRIPTION) mustBe Set(StringTag("s3"), StringTag("s4"))
    }

    "correctly add non-duplicate tags for generic tagged" in {
      // given
      val tagged = TestTagged()
      // when
      tagged.addTag(AGGREGATION, StringTag("s1"))
      tagged.addTag(AGGREGATION, StringTag("s1"))
      tagged.addTag(AGGREGATION, NamedTag("name1", StringTag("s1")))
      tagged.addTag(AGGREGATION, NamedTag("name1", StringTag("s1")))
      // then
      tagged.getTagsForType(AGGREGATION).size mustBe 2
      tagged.getTagsForType(AGGREGATION) mustBe Set(StringTag("s1"), NamedTag("name1", StringTag("s1")))

    }

  }

}
