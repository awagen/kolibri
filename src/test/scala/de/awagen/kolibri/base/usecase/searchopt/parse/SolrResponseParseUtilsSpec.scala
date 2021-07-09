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


package de.awagen.kolibri.base.usecase.searchopt.parse

import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.base.usecase.searchopt.fixtures.SolrResponseFixture
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}


class SolrResponseParseUtilsSpec extends UnitTestSpec {

  val parsed: JsValue = Json.parse(SolrResponseFixture.solrResponse1)

  "SolrResponseUtil" must {

    "correctly parse product ids in order" in {
      //given, when
      val seq: Seq[String] = SolrResponseParseUtils.retrieveProductIdsInOrderFromFullResponse(parsed)
      //then
      seq mustBe Seq("12", "22")
    }

    "correctly parse product ids in order from JsonValue" in {
      // given
      var value: JsObject = JsObject(Map.empty[String, JsValue])
      val docs: Seq[JsValue] = Seq("p1", "p2", "p3", "p4").map(pId => {
        JsObject(Map[String, JsValue]("product_id" -> JsString(pId)))
      }).toList
      val docsArray: JsArray = JsArray.apply(docs)
      value = value + ("response" ->
        JsObject(Map[String, JsValue]("docs" -> docsArray)))
      // when
      val ids: Seq[String] = SolrResponseParseUtils.retrieveProductIdsInOrderFromFullResponse(value)
      // then
      ids mustBe Seq("p1", "p2", "p3", "p4")
    }

    "correctly parse product ids in order from json value from string" in {
      // given
      val json: JsValue = Json.parse(
        """
        {"response":{"docs":[{"product_id":"p0"},{"product_id":"p9"},{"product_id":"p1"}]}}
        """)
      // when
      val ids: Seq[String] = SolrResponseParseUtils.retrieveProductIdsInOrderFromFullResponse(json)
      val ids1 = (json \ "response" \ "docs" \\ "product_id").map(x => x.as[String]).toSeq
      // then
      ids mustBe Seq("p0", "p9", "p1")
    }

    "correctly parse ids in order" in {
      //given, when
      val seq: Seq[String] = SolrResponseParseUtils.retrieveIdsInOrderFromFullResponse(parsed)
      //then
      seq mustBe Seq("11", "21")
    }

    "correctly parse solr response" in {
      //given
      val response = "{\n  \"responseHeader\":{\n    \"zkConnected\":true,\n    \"status\":0,\n    \"QTime\":26},\n  \"defaultSort\":\"score desc,kebab desc,product_id asc\",\n  \"currentSort\":\"score desc,kebab desc,product_id asc\",\n  \"response\":{\"numFound\":51,\"start\":0,\"docs\":[\n      {\n        \"product_id\":\"746660075\"},\n      {\n        \"product_id\":\"746519924\"},\n      {\n        \"product_id\":\"615031374\"},\n      {\n        \"product_id\":\"615029210\"},\n      {\n        \"product_id\":\"615031365\"},\n      {\n        \"product_id\":\"530296302\"},\n      {\n        \"product_id\":\"530297027\"},\n      {\n        \"product_id\":\"612670094\"},\n      {\n        \"product_id\":\"623520805\"},\n      {\n        \"product_id\":\"493967813\"},\n      {\n        \"product_id\":\"612670050\"},\n      {\n        \"product_id\":\"688912814\"},\n      {\n        \"product_id\":\"494029980\"},\n      {\n        \"product_id\":\"672323696\"},\n      {\n        \"product_id\":\"822088847\"},\n      {\n        \"product_id\":\"622597118\"},\n      {\n        \"product_id\":\"612669958\"},\n      {\n        \"product_id\":\"640790168\"},\n      {\n        \"product_id\":\"508402820\"},\n      {\n        \"product_id\":\"530297133\"},\n      {\n        \"product_id\":\"591331375\"},\n      {\n        \"product_id\":\"746660119\"},\n      {\n        \"product_id\":\"528697763\"},\n      {\n        \"product_id\":\"529258110\"},\n      {\n        \"product_id\":\"639864329\"},\n      {\n        \"product_id\":\"746660031\"},\n      {\n        \"product_id\":\"681158066\"},\n      {\n        \"product_id\":\"627858301\"},\n      {\n        \"product_id\":\"450907659\"},\n      {\n        \"product_id\":\"435837471\"},\n      {\n        \"product_id\":\"666680030\"},\n      {\n        \"product_id\":\"627835646\"},\n      {\n        \"product_id\":\"691652932\"},\n      {\n        \"product_id\":\"715627078\"},\n      {\n        \"product_id\":\"642070404\"},\n      {\n        \"product_id\":\"665348931\"},\n      {\n        \"product_id\":\"818541440\"},\n      {\n        \"product_id\":\"626947797\"},\n      {\n        \"product_id\":\"692499926\"},\n      {\n        \"product_id\":\"639864330\"},\n      {\n        \"product_id\":\"746660053\"},\n      {\n        \"product_id\":\"681158265\"},\n      {\n        \"product_id\":\"757938246\"},\n      {\n        \"product_id\":\"641287081\"},\n      {\n        \"product_id\":\"772367546\"},\n      {\n        \"product_id\":\"692499916\"},\n      {\n        \"product_id\":\"772367489\"},\n      {\n        \"product_id\":\"681850658\"},\n      {\n        \"product_id\":\"582486594\"},\n      {\n        \"product_id\":\"637192125\"},\n      {\n        \"product_id\":\"687404754\"}]\n  },\n  \"spellcheck\":{\n    \"suggestions\":[],\n    \"collations\":[]}}"
      val parsed = Json.parse(response)
      //when
      val ids = SolrResponseParseUtils.retrieveProductIdsInOrderFromFullResponse(parsed)
      //then
      ids.size mustBe 51
      ids.slice(0, 5) mustBe Seq("746660075", "746519924", "615031374", "615029210", "615031365")
    }

  }


}
