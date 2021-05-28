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

import play.api.libs.json.JsValue

object SolrResponseParseUtils {

  def retrieveIdsInOrderFromFullResponse(response: JsValue): Seq[String] = {
    (response \ "response" \ "docs" \\ "id").map(_.as[String]).toSeq
  }

  def retrieveProductIdsInOrderFromFullResponse(response: JsValue): Seq[String] = {
    (response \ "response" \ "docs" \\ "product_id").map(_.as[String]).toSeq
  }

  def retrieveStatusFromFullResponse(response: JsValue): Int = {
    (response \ "responseHeader" \ "status").get.as[Int]
  }

  def retrieveErrorFromFullResponse(response: JsValue): Int = {
    (response \ "error" \ "status").get.as[Int]
  }

}
