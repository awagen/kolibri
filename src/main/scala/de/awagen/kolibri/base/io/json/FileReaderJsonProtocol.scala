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

import de.awagen.kolibri.base.io.reader.{FileReader, LocalResourceFileReader}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsObject, JsString, JsValue, JsonFormat}

object FileReaderJsonProtocol extends DefaultJsonProtocol {

  implicit object FileReaderFormat extends JsonFormat[FileReader] {
    override def read(json: JsValue): FileReader = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "LOCAL_FILE_READER" =>
          val delimiter = fields.get("delimiter")
          val position = fields.get("position")
          val fromClasspath = fields("fromClasspath").convertTo[Boolean]
          val encoding = fields.get("encoding").map(x => x.convertTo[String])
          val delimiterAndPosition: Option[(String, Int)] = for {x <- delimiter; y <- position} yield (x.convertTo[String], y.convertTo[Int])
          LocalResourceFileReader(delimiterAndPosition, fromClasspath, encoding.getOrElse("UTF-8"))
      }
      case e => throw DeserializationException(s"Expected a value from ValueProvider but got value $e")
    }

    // TODO: check correctness
    override def write(obj: FileReader): JsValue = obj match {
      case e if e.isInstanceOf[LocalResourceFileReader] =>
        var fieldMap: Map[String, JsValue] = JsString(obj.toString).asJsObject.fields
        fieldMap = fieldMap + ("type" -> JsString("LOCAL_FILE_READER"))
        JsString(JsObject(fieldMap).toString())
      case e =>
        JsString(e.toString)
    }
  }

}
