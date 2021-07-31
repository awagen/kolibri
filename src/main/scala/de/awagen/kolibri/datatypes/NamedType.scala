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


package de.awagen.kolibri.datatypes

import play.api.libs.json.JsValue
import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, DoubleJsonFormat, FloatJsonFormat, StringJsonFormat, immSeqFormat}


/**
  * Serializable type enum to generate NamedClassTyped instances (e.g usable as keys of TypeTaggedMap).
  * Also provides cast functions to cast a play or spray json JsValue to specific type
  */
object NamedType extends Enumeration {
  type NamedType = Val

  case class Val(typeName: String) extends super.Val {
    def toNamedClassType(name: String): NamedClassTyped[_] = typeName match {
      case "STRING" => NamedClassTyped[String](name)
      case "DOUBLE" => NamedClassTyped[Double](name)
      case "FLOAT" => NamedClassTyped[Float](name)
      case "BOOLEAN" => NamedClassTyped[Boolean](name)
      case "SEQ[STRING]" => NamedClassTyped[Seq[String]](name)
      case "SEQ[DOUBLE]" => NamedClassTyped[Seq[Double]](name)
      case "SEQ[FLOAT]" => NamedClassTyped[Seq[Float]](name)
      case "SEQ[BOOLEAN]" => NamedClassTyped[Seq[Boolean]](name)
      case _ => throw new RuntimeException(s"type $typeName not available")
    }

    def cast(value: JsValue): Any = typeName match {
      case "STRING" => value.as[String]
      case "DOUBLE" => value.as[Double]
      case "FLOAT" => value.as[Float]
      case "BOOLEAN" => value.as[Boolean]
      case "SEQ[STRING]" => value.as[Seq[String]]
      case "SEQ[DOUBLE]" => value.as[Seq[Double]]
      case "SEQ[FLOAT]" => value.as[Seq[Float]]
      case "SEQ[BOOLEAN]" => value.as[Seq[Boolean]]
      case e => new RuntimeException(s"value '$value' does not conform to type corresponding to typeName $typeName")
    }

    def cast(value: spray.json.JsValue): Any = typeName match {
      case "STRING" => value.convertTo[String]
      case "DOUBLE" => value.convertTo[Double]
      case "FLOAT" => value.convertTo[Float]
      case "BOOLEAN" => value.convertTo[Boolean]
      case "SEQ[STRING]" => value.convertTo[Seq[String]]
      case "SEQ[DOUBLE]" => value.convertTo[Seq[Double]]
      case "SEQ[FLOAT]" => value.convertTo[Seq[Float]]
      case "SEQ[BOOLEAN]" => value.convertTo[Seq[Boolean]]
      case e => new RuntimeException(s"value '$value' does not conform to type corresponding to typeName $typeName")
    }
  }

  val STRING: Val = Val("STRING")
  val DOUBLE: Val = Val("DOUBLE")
  val FLOAT: Val = Val("FLOAT")
  val BOOLEAN: Val = Val("BOOLEAN")
  val SEQ_STRING: Val = Val("SEQ[STRING]")
  val SEQ_DOUBLE: Val = Val("SEQ[DOUBLE]")
  val SEQ_FLOAT: Val = Val("SEQ[FLOAT]")
  val SEQ_BOOLEAN: Val = Val("SEQ[BOOLEAN]")

}