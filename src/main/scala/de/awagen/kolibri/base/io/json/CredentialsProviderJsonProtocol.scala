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

import de.awagen.kolibri.base.domain.jobdefinitions.provider.{BaseCredentialsProvider, Credentials, CredentialsProvider}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat}

object CredentialsProviderJsonProtocol extends DefaultJsonProtocol {

  implicit object CredentialsProviderFormat extends JsonFormat[CredentialsProvider] {
    override def read(json: JsValue): CredentialsProvider = json match {
      case spray.json.JsObject(fields) if fields.contains("username") &&
        fields.contains("password") =>
        BaseCredentialsProvider(Credentials(fields("username").convertTo[String], fields("password").convertTo[String]))
      case e => throw DeserializationException(s"Expected a value from Credentials but got value $e")
    }

    override def write(obj: CredentialsProvider): JsValue = JsString(obj.toString)
  }

}
