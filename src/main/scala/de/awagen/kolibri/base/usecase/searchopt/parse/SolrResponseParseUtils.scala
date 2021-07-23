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

/**
  * Distinct json path parsing definitions
  */
object SolrResponseParseUtils {

  /**
    * Retrieve all ids under response.docs, assuming format {"response": {"docs": [{"id": ...},...],...},...}
    *
    * @param value
    * @return
    */
  def retrieveIdsInOrderFromFullResponse(value: JsValue): Seq[String] = {
    (value \ "response" \ "docs" \\ "id").map(_.as[String]).toSeq
  }

  /**
    * Retrieve all ids under response.docs, assuming format {"response": {"docs": [{"product_id": ...},...],...},...}
    *
    * @param value
    * @return
    */
  def retrieveProductIdsInOrderFromFullResponse(value: JsValue): Seq[String] = {
    (value \ "response" \ "docs" \\ "product_id").map(_.as[String]).toSeq
  }

  /**
    * Pick response status from selector responseHeader.status
    *
    * @param value
    * @return
    */
  def retrieveStatusFromFullResponse(value: JsValue): Int = {
    (value \ "responseHeader" \ "status").get.as[Int]
  }

  /**
    * Retrieve error status from selector error.status
    *
    * @param value
    * @return
    */
  def retrieveErrorFromFullResponse(value: JsValue): Int = {
    (value \ "error" \ "status").get.as[Int]
  }

}
