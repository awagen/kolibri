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

import play.api.libs.json.{JsResult, JsSuccess, JsValue, Reads}

trait SolrReads {

  /**
    * Reads[SolrResponse] as used when matching JsValue to SolrResponse object
    */
  implicit object solrResponseReads extends Reads[Either[Throwable, Seq[String]]] {
    def reads(json: JsValue): JsResult[Either[Throwable, Seq[String]]] = {
      try {
        val ids: Seq[String] = (json \ "response" \ "docs" \\ "product_id").map(_.as[String]).toSeq
        JsSuccess(Right(ids))
      }
      catch {
        case e: Exception => JsSuccess(Left(e))
      }
    }
  }

}
