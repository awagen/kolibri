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


package de.awagen.kolibri.base.processing.classifier

import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1

object Mapper {


  trait FilteringMapper[-U, +V] {

    def accept(element: U): Boolean

    def map(element: U): V

  }

  class BaseFilteringMapper[U, V](acceptDecider: SerializableFunction1[U, Boolean],
                                  mapper: SerializableFunction1[U, V]) extends FilteringMapper[U, V] {
    override def accept(element: U): Boolean = acceptDecider.apply(element)

    override def map(element: U): V = mapper.apply(element)
  }

  class TagFilteringMapper(acceptDecider: SerializableFunction1[Tag, Boolean],
                           mapper: SerializableFunction1[Tag, Tag]) extends BaseFilteringMapper[Tag, Tag](acceptDecider, mapper)

  class AcceptAllAsIdentityMapper[U] extends BaseFilteringMapper[U, U](
    acceptDecider = new SerializableFunction1[U, Boolean] {
      override def apply(v1: U): Boolean = true
    },
    mapper = new SerializableFunction1[U, U] {
      override def apply(v1: U): U = v1
    })

}
