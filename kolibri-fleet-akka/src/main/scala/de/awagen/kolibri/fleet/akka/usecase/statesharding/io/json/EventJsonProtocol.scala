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


package de.awagen.kolibri.fleet.akka.usecase.statesharding.io.json

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.awagen.kolibri.fleet.akka.usecase.statesharding.actors.EventAggregatingActor.{KeyValueEvent, CombinedEvent, EntityEvent}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object EventJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {

  implicit val entityEventFormat: RootJsonFormat[EntityEvent] = jsonFormat4(EntityEvent)
  implicit val keyValueEventFormat: RootJsonFormat[KeyValueEvent] = jsonFormat5(KeyValueEvent)
  implicit val combinedEventFormat: RootJsonFormat[CombinedEvent] = jsonFormat4(CombinedEvent)

}
