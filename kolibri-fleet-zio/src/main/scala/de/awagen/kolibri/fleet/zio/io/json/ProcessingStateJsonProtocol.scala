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


package de.awagen.kolibri.fleet.zio.io.json

import de.awagen.kolibri.fleet.zio.io.json.EnumerationJsonProtocol.processingStatusFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{ProcessId, ProcessingInfo, ProcessingState}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object ProcessingStateJsonProtocol extends DefaultJsonProtocol {

  implicit val processIdFormat: RootJsonFormat[ProcessId] = jsonFormat2(ProcessId)
  implicit val processingInfoFormat: RootJsonFormat[ProcessingInfo] = jsonFormat5(ProcessingInfo)
  implicit val processingStateFormat: RootJsonFormat[ProcessingState] = jsonFormat2(ProcessingState)

}
