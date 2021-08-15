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


package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.format.Formats
import de.awagen.kolibri.base.format.Formats.MetricDocumentStringFormat
import de.awagen.kolibri.datatypes.stores.MetricDocument
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, JsonFormat, enrichAny}

object FormatJsonProtocol extends DefaultJsonProtocol {

  implicit object FileReaderFormat extends JsonFormat[Formats.StringFormat[MetricDocument[_]]] {
    // TODO
    override def write(obj: Formats.StringFormat[MetricDocument[_]]): JsValue = """{}""".toJson

    override def read(json: JsValue): Formats.StringFormat[MetricDocument[_]] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "CSV" =>
          MetricDocumentStringFormat(fields("columnSeparator").convertTo[String])
      }
      case e => throw DeserializationException(s"Expected a value from StringFormat[MetricDocument[_]] but got value $e")
    }
  }

}
