/**
 * Copyright 2022 Andreas Wagenmann
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

package de.awagen.kolibri.datatypes.io.json

import de.awagen.kolibri.datatypes.io.json.ClassTypedJsonProtocol.TypeKeys._
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{NestedFieldSeqStructDef, RegexStructDef, StringConstantStructDef, StringSeqChoiceStructDef}
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, NamedClassTyped, WithStructDef}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, JsonFormat, enrichAny}

object NamedClassTypedKeyJsonProtocol extends DefaultJsonProtocol {

  val NAME_KEY = "name"
  val TYPE_KEY = "type"


  implicit object NamedClassTypedKeyFormat extends JsonFormat[NamedClassTyped[_]] with WithStructDef {
    override def read(json: JsValue): NamedClassTyped[_] = json match {
      case spray.json.JsObject(fields) if fields.contains(NAME_KEY) && fields.contains(TYPE_KEY) =>
        val name = fields(NAME_KEY).convertTo[String]
        fields(TYPE_KEY).convertTo[String] match {
          case STRING_TYPE => NamedClassTyped[String](name)
          case DOUBLE_TYPE => NamedClassTyped[Double](name)
          case FLOAT_TYPE => NamedClassTyped[Float](name)
          case BOOLEAN_TYPE => NamedClassTyped[Boolean](name)
          case SEQ_STRING_TYPE => NamedClassTyped[Seq[String]](name)
          case SEQ_DOUBLE_TYPE => NamedClassTyped[Seq[Double]](name)
          case SEQ_FLOAT_TYPE => NamedClassTyped[Seq[Float]](name)
          case SEQ_BOOLEAN_TYPE => NamedClassTyped[Seq[Boolean]](name)
          case MAP_STRING_DOUBLE => NamedClassTyped[Map[String, Double]](name)
          case MAP_STRING_FLOAT => NamedClassTyped[Map[String, Float]](name)
          case MAP_STRING_STRING => NamedClassTyped[Map[String, String]](name)
          case MAP_STRING_SEQ_STRING => NamedClassTyped[Map[String, Seq[String]]](name)
          case e => throw DeserializationException(s"Expected a defined type for ClassTyped, but got: $e")
        }
      case e => throw DeserializationException(s"Expected a value of type NamedClassType[T] but got value $e")
    }

    // TODO: this write is not correct, need to explicitly map to types to use right identifiers
    override def write(obj: NamedClassTyped[_]): JsValue = {
      val typeIdent = obj.classType.toString.toUpperCase
      s"""{"name": "${obj.name}", "type": "$typeIdent"}""".toJson
    }

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(NAME_KEY),
            RegexStructDef("[a-zA-Z]\\w*".r),
            required = true
          ),
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringSeqChoiceStructDef(Seq(
              STRING_TYPE,
              DOUBLE_TYPE,
              FLOAT_TYPE,
              BOOLEAN_TYPE,
              SEQ_STRING_TYPE,
              SEQ_DOUBLE_TYPE,
              SEQ_FLOAT_TYPE,
              SEQ_BOOLEAN_TYPE
            )),
            required = true)
        ),
        Seq.empty
      )
    }
  }

}
