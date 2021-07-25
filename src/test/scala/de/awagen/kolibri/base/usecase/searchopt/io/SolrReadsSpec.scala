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


package de.awagen.kolibri.base.usecase.searchopt.io

import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.base.usecase.searchopt.fixtures.SolrResponseFixture
import play.api.libs.json.{JsResult, JsValue, Json}


class SolrReadsSpec extends UnitTestSpec with SolrReads {

  "SolrReads" must {

    "correctly extract doc product_ids in order" in {
      //given
      val parsed: JsValue = Json.parse(SolrResponseFixture.solrResponse1)
      //when
      val obj: JsResult[Either[Throwable, Seq[String]]] = solrResponseReads.reads(parsed)
      //then
      val ids = obj.get match {
        case Right(productIds) =>
          productIds
        case Left(_) => Seq()
      }
      //then
      ids mustBe Seq("12", "22")
    }
  }

}
