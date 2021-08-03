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

import akka.http.scaladsl.model.{ContentType, ContentTypes}
import de.awagen.kolibri.base.processing.modifiers.RequestPermutation
import de.awagen.kolibri.datatypes.io.json.OrderedMultiValuesJsonProtocol._
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


object RequestPermutationJsonProtocol extends DefaultJsonProtocol {

  def stringToContentType(contentType: String): ContentType = contentType match {
    case "json" => ContentTypes.`application/json`
    case "plain_utf8" => ContentTypes.`text/plain(UTF-8)`
    case "csv" => ContentTypes.`text/csv(UTF-8)`
    case "xml" => ContentTypes.`text/xml(UTF-8)`
    case "html" => ContentTypes.`text/html(UTF-8)`
    case _ => ContentTypes.`application/json`
  }

  implicit val requestPermutationFormat: RootJsonFormat[RequestPermutation] = jsonFormat(
    (
      params: OrderedMultiValues,
      headers: OrderedMultiValues,
      bodies: Seq[String],
      bodyContentType: String
    ) => RequestPermutation.apply(params, headers, bodies, stringToContentType(bodyContentType)),
    "params",
    "headers",
    "bodies",
    "bodyContentType"
  )

}
