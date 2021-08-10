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

import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.TagType.TagType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag

trait TaggedWithType extends KolibriSerializable {

  private[this] var tags: Map[TagType, Set[Tag]] = Map.empty[TagType, Set[Tag]]

  def extendAllWithTag(tagType: TagType, tag: Tag, filter: Tag => Boolean): Unit = {
    if (!tags.contains(tagType)) {
      tags = tags + (tagType -> Set(tag))
    }
    else {
      val tagSet: Set[Tag] = tags(tagType)
      val newTagSet: Set[Tag] = tagSet.map(t => t.extendAllWithTag(tag, filter))
      tags = tags + (tagType -> newTagSet)
    }
  }

  /**
    * add single tag for given type
    *
    * @param tagType
    * @param tag
    */
  def addTag(tagType: TagType, tag: Tag): Unit = {
    if (!tags.contains(tagType)) {
      tags = tags + (tagType -> Set(tag))
    }
    else {
      val set: Set[Tag] = tags(tagType).+(tag)
      tags = tags + (tagType -> set)
    }
  }

  /**
    * Add multiple tags for given type
    *
    * @param tagType
    * @param tags
    */
  def addTags(tagType: TagType, tags: Set[Tag]): Unit = {
    tags.foreach(tag => addTag(tagType, tag))
  }

  /**
    * get all tags (all types)
    *
    * @return
    */
  def getTags: Map[TagType, Set[Tag]] = tags

  /**
    * get tags for specific type
    *
    * @param tagType
    * @return
    */
  def getTagsForType(tagType: TagType): Set[Tag] = tags.getOrElse(tagType, Set.empty)

}
