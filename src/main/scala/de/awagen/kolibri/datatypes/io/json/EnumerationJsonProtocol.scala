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

import de.awagen.kolibri.datatypes.NamedType
import de.awagen.kolibri.datatypes.NamedType.NamedType
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat}

object EnumerationJsonProtocol extends DefaultJsonProtocol {

  trait EnumerationProtocol[T] extends JsonFormat[T] {
    override def write(obj: T): JsValue = JsString(obj.toString)
  }

  implicit object namedTypesFormat extends EnumerationProtocol[NamedType] {
    override def read(json: JsValue): NamedType.Val = {
      json match {
        case JsString(txt) => NamedType.withName(txt).asInstanceOf[NamedType]
        case e => throw DeserializationException(s"Expected a value from Metrics but got value $e")
      }
    }

    override def write(value: NamedType): JsValue = JsString(value.typeName)

  }
}
