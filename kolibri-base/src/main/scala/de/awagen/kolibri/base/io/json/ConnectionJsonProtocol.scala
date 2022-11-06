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

import de.awagen.kolibri.base.config.AppProperties
import de.awagen.kolibri.base.domain.Connections.Connection
import de.awagen.kolibri.base.domain.jobdefinitions.provider.CredentialsProvider
import de.awagen.kolibri.base.io.json.CredentialsProviderJsonProtocol.CredentialsProviderFormat
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


object ConnectionJsonProtocol extends DefaultJsonProtocol with WithStructDef {

  val HOST_FIELD = "host"
  val PORT_FIELD = "port"
  val USE_HTTPS_FIELD = "useHttps"
  val CREDENTIALS_PROVIDER_FIELD = "credentialsProvider"

  implicit val connectionFormat: RootJsonFormat[Connection] = jsonFormat(
    (host: String, port: Int, useHttps: Boolean, credentialsProvider: Option[CredentialsProvider]) => Connection
      .apply(host, port, useHttps, credentialsProvider),
    HOST_FIELD,
    PORT_FIELD,
    USE_HTTPS_FIELD,
    CREDENTIALS_PROVIDER_FIELD
  )

  override def structDef: JsonStructDefs.StructDef[_] = {
    val allowedHosts = AppProperties.config.allowedRequestTargetHosts
    val allowedTargetPorts = AppProperties.config.allowedRequestTargetPorts

    val hostFieldDef = allowedHosts match {
      case e if e.contains("*") =>
        FieldDef(StringConstantStructDef(HOST_FIELD), RegexStructDef("\\w+".r), required = true)
      case _ =>
        FieldDef(StringConstantStructDef(HOST_FIELD), StringChoiceStructDef(allowedHosts), required = true)
    }

    val portFieldDef = allowedTargetPorts match {
      case e if e.contains("*") =>
        FieldDef(StringConstantStructDef(PORT_FIELD), IntMinMaxStructDef(0, 10000), required = true)
      case _ =>
        FieldDef(StringConstantStructDef(PORT_FIELD), IntChoiceStructDef(
          allowedTargetPorts.map(x => x.toInt)
        ), required = true)
    }

    NestedFieldSeqStructDef(
      Seq(
        hostFieldDef,
        portFieldDef,
        FieldDef(StringConstantStructDef(USE_HTTPS_FIELD), BooleanStructDef, required = true),
      ),
      Seq()
    )
  }
}
