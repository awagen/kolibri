/**
 * Copyright 2022 Andreas Wagenmann
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


package de.awagen.kolibri.datatypes.io.json

import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.{DeserializationException, JsString, JsValue, JsonFormat}

object ComputeFailReasonJsonProtocol {

  implicit object ComputeFailReasonFormat extends JsonFormat[ComputeFailReason] {
    override def read(json: JsValue): ComputeFailReason = json.convertTo[String] match {
      case "NO_RESULTS" =>
        ComputeFailReason.NO_RESULTS
      case "ZERO_DENOMINATOR" =>
        ComputeFailReason.ZERO_DENOMINATOR
      case "FAILED_HISTOGRAM" =>
        ComputeFailReason.FAILED_HISTOGRAM
      case e =>
        throw DeserializationException(s"Expected a value from ComputeFailReason but got value $e")
    }

    override def write(obj: ComputeFailReason): JsValue = obj match {
      case _ => JsString(obj.description)
    }
  }

}
