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

import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{NestedFieldSeqStructDef, RegexStructDef, StringConstantStructDef, StringSeqChoiceStructDef}
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, NamedClassTyped, WithStructDef}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, JsonFormat, enrichAny}

object NamedClassTypedKeyJsonProtocol extends DefaultJsonProtocol {

  val NAME_KEY = "name"
  val TYPE_KEY = "type"
  val STRING_TYPE = "STRING"
  val DOUBLE_TYPE = "DOUBLE"
  val FLOAT_TYPE = "FLOAT"
  val BOOLEAN_TYPE = "BOOLEAN"
  val SEQ_STRING_TYPE = "SEQ[STRING]"
  val SEQ_DOUBLE_TYPE = "SEQ[DOUBLE]"
  val SEQ_FLOAT_TYPE = "SEQ[FLOAT]"
  val SEQ_BOOLEAN_TYPE = "SEQ[BOOLEAN]"

  implicit object NamedClassTypedKeyFormat extends JsonFormat[NamedClassTyped[_]] with WithStructDef {
    override def read(json: JsValue): NamedClassTyped[_] = json match {
      case spray.json.JsObject(fields) if fields.contains(NAME_KEY) && fields.contains(TYPE_KEY) => fields(TYPE_KEY).convertTo[String] match {
        case STRING_TYPE  => NamedClassTyped[String](fields(NAME_KEY).convertTo[String])
        case DOUBLE_TYPE => NamedClassTyped[Double](fields(NAME_KEY).convertTo[String])
        case FLOAT_TYPE => NamedClassTyped[Float](fields(NAME_KEY).convertTo[String])
        case BOOLEAN_TYPE => NamedClassTyped[Boolean](fields(NAME_KEY).convertTo[String])
        case SEQ_STRING_TYPE => NamedClassTyped[Seq[String]](fields(NAME_KEY).convertTo[String])
        case SEQ_DOUBLE_TYPE => NamedClassTyped[Seq[Double]](fields(NAME_KEY).convertTo[String])
        case SEQ_FLOAT_TYPE => NamedClassTyped[Seq[Float]](fields(NAME_KEY).convertTo[String])
        case SEQ_BOOLEAN_TYPE => NamedClassTyped[Seq[Boolean]](fields(NAME_KEY).convertTo[String])
        case e => throw DeserializationException(s"Expected a defined type for ClassTyped, but got: $e")
      }
      case e => throw DeserializationException(s"Expected a value of type NamedClassType[T] but got value $e")
    }

    override def write(obj: NamedClassTyped[_]): JsValue = {
      val typeIdent = obj.classType.toString.toUpperCase
      s"""{"name": "${obj.name}", "type": "$typeIdent"}""".toJson
    }

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          // TODO: refine regex
          FieldDef(StringConstantStructDef(NAME_KEY), RegexStructDef(".*".r), required = true),
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
