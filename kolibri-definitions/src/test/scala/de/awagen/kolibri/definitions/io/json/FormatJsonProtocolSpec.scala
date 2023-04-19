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


package de.awagen.kolibri.definitions.io.json

import de.awagen.kolibri.definitions.format.Formats
import de.awagen.kolibri.definitions.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.stores.MetricDocument
import spray.json._
import FormatJsonProtocol._


class FormatJsonProtocolSpec extends UnitTestSpec {

  val metricDocumentStringFormatJson: JsValue =
    """
      |{
      |"type": "CSV",
      |"columnSeparator": "\t"
      |}
      |""".stripMargin.parseJson

  "FormatJsonProtocol" must {

    "correctly parse StringFormat[MetricDocument[_]]" in {
      val metricDocumentFormat = metricDocumentStringFormatJson.convertTo[Formats.StringFormat[MetricDocument[_]]]
      metricDocumentFormat.isInstanceOf[Formats.StringFormat[MetricDocument[_]]] mustBe true
    }
  }

}
