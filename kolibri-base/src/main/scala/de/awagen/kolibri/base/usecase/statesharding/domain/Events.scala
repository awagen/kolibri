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


package de.awagen.kolibri.base.usecase.statesharding.domain

import de.awagen.kolibri.base.usecase.statesharding.domain.Events.EntityType.EntityType
import de.awagen.kolibri.datatypes.io.KolibriSerializable

object Events {

  trait Event extends KolibriSerializable
  case class Search(query: String, count: Int, whoId: String) extends Event
  case class Click(query: String, entityType: EntityType, entityId: String, count: Int, whoId: String) extends Event
  case class Add2Basket(query: String, entityId: String, count: Int, whoId: String) extends Event

  object EntityType extends Enumeration {
    type EntityType = super.Value
    val PRODUCT_ID, CATEGORY_ID = super.Value
  }

  object UserEventType extends Enumeration {
    type UserEventType = super.Value
    val INTERACTION_EVENT, SEARCH_EVENT = super.Value
  }

}
