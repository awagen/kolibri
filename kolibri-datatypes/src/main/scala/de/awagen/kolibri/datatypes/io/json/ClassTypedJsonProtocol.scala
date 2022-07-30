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

import de.awagen.kolibri.datatypes.types.JsonStructDefs.StringChoiceStructDef
import de.awagen.kolibri.datatypes.types.{ClassTyped, JsonStructDefs, WithStructDef}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import scala.reflect.runtime.universe.typeOf

object ClassTypedJsonProtocol extends DefaultJsonProtocol {

  object TypeKeys {
    val STRING_TYPE = "STRING"
    val DOUBLE_TYPE = "DOUBLE"
    val FLOAT_TYPE = "FLOAT"
    val BOOLEAN_TYPE = "BOOLEAN"
    val SEQ_STRING_TYPE = "SEQ[STRING]"
    val SEQ_DOUBLE_TYPE = "SEQ[DOUBLE]"
    val SEQ_FLOAT_TYPE = "SEQ[FLOAT]"
    val SEQ_BOOLEAN_TYPE = "SEQ[BOOLEAN]"
    val MAP_STRING_DOUBLE = "MAP[STRING,DOUBLE]"
    val MAP_STRING_FLOAT = "MAP[STRING,FLOAT]"
    val MAP_STRING_STRING = "MAP[STRING,STRING]"
    val MAP_STRING_SEQ_STRING = "MAP[STRING,SEQ[STRING]]"
  }

  implicit object ClassTypedFormat extends JsonFormat[ClassTyped[_]] with WithStructDef {

    import TypeKeys._

    override def read(json: JsValue): ClassTyped[_] = json match {
      case JsString(value) => value match {
        case STRING_TYPE => ClassTyped[String]
        case DOUBLE_TYPE => ClassTyped[Double]
        case FLOAT_TYPE => ClassTyped[Float]
        case BOOLEAN_TYPE => ClassTyped[Boolean]
        case SEQ_STRING_TYPE => ClassTyped[Seq[String]]
        case SEQ_DOUBLE_TYPE => ClassTyped[Seq[Double]]
        case SEQ_FLOAT_TYPE => ClassTyped[Seq[Float]]
        case SEQ_BOOLEAN_TYPE => ClassTyped[Seq[Boolean]]
        case MAP_STRING_DOUBLE => ClassTyped[Map[String, Double]]
        case MAP_STRING_FLOAT => ClassTyped[Map[String, Float]]
        case MAP_STRING_STRING => ClassTyped[Map[String, String]]
        case MAP_STRING_SEQ_STRING => ClassTyped[Map[String, Seq[String]]]
      }
    }

    override def write(obj: ClassTyped[_]): JsValue = obj.classType match {
      case t if t =:= typeOf[String] => JsString(STRING_TYPE)
      case t if t =:= typeOf[Double] => JsString(DOUBLE_TYPE)
      case t if t =:= typeOf[Float] => JsString(FLOAT_TYPE)
      case t if t =:= typeOf[Boolean] => JsString(BOOLEAN_TYPE)
      case t if t =:= typeOf[Seq[String]] => JsString(SEQ_STRING_TYPE)
      case t if t =:= typeOf[Seq[Double]] => JsString(SEQ_DOUBLE_TYPE)
      case t if t =:= typeOf[Seq[Float]] => JsString(SEQ_FLOAT_TYPE)
      case t if t =:= typeOf[Seq[Boolean]] => JsString(SEQ_BOOLEAN_TYPE)
      case t if t =:= typeOf[Map[String, Double]] => JsString(MAP_STRING_DOUBLE)
      case t if t =:= typeOf[Map[String, Float]] => JsString(MAP_STRING_FLOAT)
      case t if t =:= typeOf[Map[String, String]] => JsString(MAP_STRING_STRING)
      case t if t =:= typeOf[Map[String, Seq[String]]] => JsString(MAP_STRING_SEQ_STRING)
    }

    override def structDef: JsonStructDefs.StructDef[_] = StringChoiceStructDef(Seq(
      STRING_TYPE,
      DOUBLE_TYPE,
      FLOAT_TYPE,
      BOOLEAN_TYPE,
      SEQ_STRING_TYPE,
      SEQ_DOUBLE_TYPE,
      SEQ_FLOAT_TYPE,
      SEQ_BOOLEAN_TYPE,
      MAP_STRING_DOUBLE,
      MAP_STRING_FLOAT,
      MAP_STRING_STRING,
      MAP_STRING_SEQ_STRING
    ))
  }

}
