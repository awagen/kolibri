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

import de.awagen.kolibri.definitions.domain.jobdefinitions.provider.{BaseCredentialsProvider, Credentials, CredentialsProvider}
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{NestedFieldSeqStructDef, RegexStructDef, StringConstantStructDef}
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat}

object CredentialsProviderJsonProtocol extends DefaultJsonProtocol {

  implicit object CredentialsProviderFormat extends JsonFormat[CredentialsProvider] with WithStructDef {
    val USERNAME_KEY = "username"
    val PASSWORD_KEY = "password"

    override def read(json: JsValue): CredentialsProvider = json match {
      case spray.json.JsObject(fields) if fields.contains(USERNAME_KEY) &&
        fields.contains(PASSWORD_KEY) =>
        BaseCredentialsProvider(Credentials(fields(USERNAME_KEY).convertTo[String], fields(PASSWORD_KEY).convertTo[String]))
      case e => throw DeserializationException(s"Expected a value from Credentials but got value $e")
    }

    override def write(obj: CredentialsProvider): JsValue = JsString(obj.toString)

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(StringConstantStructDef(USERNAME_KEY), RegexStructDef(".+".r), required = true),
          FieldDef(StringConstantStructDef(PASSWORD_KEY), RegexStructDef(".+".r), required = true),
        ),
        Seq()
      )
    }
  }

}
