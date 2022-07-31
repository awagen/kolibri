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

package de.awagen.kolibri.base.usecase.searchopt.io.json

import de.awagen.kolibri.base.directives.{Resource, ResourceType}
import de.awagen.kolibri.base.usecase.searchopt.provider.{FileBasedJudgementProviderFactory, JudgementProviderFactory}
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{ConditionalFields, NestedFieldSeqStructDef, RegexStructDef, StringConstantStructDef}
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat}


object JudgementProviderFactoryJsonProtocol extends DefaultJsonProtocol with WithStructDef {

  val TYPE_FILE_BASED_KEY = "FILE_BASED"
  val TYPE_KEY = "type"
  val FILENAME_KEY = "filename"

  implicit object JudgementProviderFactoryDoubleFormat extends JsonFormat[JudgementProviderFactory[Double]] {
    override def read(json: JsValue): JudgementProviderFactory[Double] = json match {
      case spray.json.JsObject(fields) if fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case TYPE_FILE_BASED_KEY =>
          val provider = FileBasedJudgementProviderFactory(fields(FILENAME_KEY).convertTo[String])
          provider.addResource(Resource(ResourceType.MAP_STRING_TO_DOUBLE_VALUE, provider.filename))
          provider
        case e => throw DeserializationException(s"Expected a valid type for JudgementProviderFactory but got value $e")
      }
      case e => throw DeserializationException(s"Expected a value from JudgementProviderFactory but got value $e")
    }

    override def write(obj: JudgementProviderFactory[Double]): JsValue = JsString(obj.toString)
  }

  override def structDef: JsonStructDefs.StructDef[_] =
    NestedFieldSeqStructDef(
      Seq.empty,
      Seq(
        ConditionalFields(TYPE_KEY, Map(
          TYPE_FILE_BASED_KEY -> Seq(
            FieldDef(StringConstantStructDef(FILENAME_KEY), RegexStructDef("\\w+".r), required = true)
          )
        ))
      )
    )
}
