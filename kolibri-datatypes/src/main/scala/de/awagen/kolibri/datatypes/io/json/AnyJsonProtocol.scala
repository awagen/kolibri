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

package de.awagen.kolibri.datatypes.io.json

import spray.json.{DefaultJsonProtocol, JsArray, JsFalse, JsNumber, JsObject, JsString, JsTrue, JsValue, JsonFormat}


object AnyJsonProtocol extends DefaultJsonProtocol {

  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(x: Any): JsValue = x match {
      case n: Int => JsNumber(n)
      case n: Float => JsNumber(n)
      case n: Double => JsNumber(n)
      case s: String => JsString(s)
      case b: Boolean if b => JsTrue
      case b: Boolean if !b => JsFalse
      case s: Seq[_] => new JsArray(s.map(x => write(x)).toVector)
      case m: Map[String, Any] => JsObject(m.map(x => (x._1, write(x._2))))
    }

    def read(value: JsValue): Any = value match {
      case JsNumber(n) if n.isDecimalDouble => n.doubleValue
      case JsNumber(n) if n.isDecimalFloat => n.floatValue
      case JsNumber(n) if n.isValidLong => n.longValue
      case JsNumber(n) if n.isValidInt => n.intValue
      case JsString(s) => s
      case JsTrue => true
      case JsFalse => false
      case JsArray(elements) => elements.map(x => read(x))
      case JsObject(fields) => fields.view.mapValues(x => read(x)).toMap
    }
  }

}
