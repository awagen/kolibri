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

import de.awagen.kolibri.datatypes.NamedClassTyped
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, JsonFormat, enrichAny}

object NamedClassTypedKeyJsonProtocol extends DefaultJsonProtocol {

  implicit object NamedClassTypedKeyFormat extends JsonFormat[NamedClassTyped[_]] {
    override def read(json: JsValue): NamedClassTyped[_] = json match {
      case spray.json.JsObject(fields) if fields.contains("name") && fields.contains("type") => fields("type").convertTo[String] match {
        case "STRING" => NamedClassTyped[String](fields("name").convertTo[String])
        case "DOUBLE" => NamedClassTyped[Double](fields("name").convertTo[String])
        case "FLOAT" => NamedClassTyped[Float](fields("name").convertTo[String])
        case "SEQ[STRING]" => NamedClassTyped[Seq[String]](fields("name").convertTo[String])
        case "SEQ[DOUBLE]" => NamedClassTyped[Seq[Double]](fields("name").convertTo[String])
        case "SEQ[FLOAT]" => NamedClassTyped[Seq[Float]](fields("name").convertTo[String])
        case e => throw DeserializationException(s"Expected a defined type for ClassTyped, but got: $e")
      }
      case e => throw DeserializationException(s"Expected a value of type NamedClassType[T] but got value $e")
    }

    override def write(obj: NamedClassTyped[_]): JsValue = {
      val typeIdent = obj.classType.toString.toUpperCase
      s"""{"name": "${obj.name}", "type": "$typeIdent"}""".toJson
    }
  }

}
