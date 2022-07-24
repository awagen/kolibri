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

import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{ConditionalFields, MapStructDef, NestedFieldSeqStructDef, StringChoiceStructDef, StringConstantStructDef, StringSeqStructDef, StringStructDef}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import spray.json._

import scala.io.Source

object SupplierJsonProtocol extends DefaultJsonProtocol {

  val TYPE_KEY = "type"
  val VALUE_KEY = "value"
  val FILE_KEY = "file"
  val FROM_JSON_TYPE = "FROM_JSON"
  val FROM_JSON_FILE_TYPE = "FROM_JSON_FILE"

  implicit object StringSeqMappingFormat extends JsonFormat[() => Map[String, Seq[String]]] with WithStructDef {
    override def read(json: JsValue): () => Map[String, Seq[String]] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case FROM_JSON_TYPE =>
          new SerializableSupplier[Map[String, Seq[String]]]() {
            override def apply(): Map[String, Seq[String]] = fields(VALUE_KEY).convertTo[Map[String, Seq[String]]]
          }
        case FROM_JSON_FILE_TYPE =>
          val file = fields(FILE_KEY).convertTo[String]
          val persistenceModule = AppConfig.persistenceModule
          val source: Source = persistenceModule.persistenceDIModule.reader.getSource(file)
          val mapping = source.getLines().mkString("\n").parseJson.convertTo[Map[String, Seq[String]]]
          new SerializableSupplier[Map[String, Seq[String]]]() {
            override def apply(): Map[String, Seq[String]] = mapping
          }
      }
      case e => throw DeserializationException(s"Expected a valid value vor () => Map[String, Seq[String]] but got value $e")
    }

    override def write(obj: () => Map[String, Seq[String]]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              FROM_JSON_TYPE,
              FROM_JSON_FILE_TYPE
            )),
            required = true
          )
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              FROM_JSON_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(VALUE_KEY),
                  MapStructDef(StringStructDef, StringSeqStructDef),
                  required = true
                )
              ),
              FROM_JSON_FILE_TYPE -> Seq(
                FieldDef(
                  StringConstantStructDef(FILE_KEY),
                  StringStructDef,
                  required = true
                )
              )
            )
          )
        )
      )
    }
  }


}
