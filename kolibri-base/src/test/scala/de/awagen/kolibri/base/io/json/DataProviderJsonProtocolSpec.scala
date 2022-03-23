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

import de.awagen.kolibri.base.domain.jobdefinitions.provider.data.DataProviders.OrderedMultiValuesProvider
import de.awagen.kolibri.base.io.json.DataProviderJsonProtocol.orderedMultiValuesFormat
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues}
import de.awagen.kolibri.datatypes.values.DistinctValues
import spray.json._

class DataProviderJsonProtocolSpec extends UnitTestSpec {

  val values: JsValue =
    """
      |{
      |"data": {
      | "type": "GRID_FROM_VALUES_SEQ_TYPE", "values":[{"name": "test", "values": [0.45, 0.32]}]
      |}
      |}
      |""".stripMargin.parseJson

  "DataProviderJsonProtocol" must {

    "parse OrderedMultiValuesProvider" in {
      val data: OrderedMultiValues = values.convertTo[OrderedMultiValuesProvider].data
      data mustBe GridOrderedMultiValues(Seq(DistinctValues[Double]("test", Seq(0.45, 0.32))))
    }

  }

}
