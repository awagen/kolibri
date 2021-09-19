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

import com.softwaremill.macwire.wire
import de.awagen.kolibri.base.config.di.modules.persistence.PersistenceModule
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableSupplier
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, JsonFormat, enrichAny}
import spray.json._

import scala.io.Source

object SupplierJsonProtocol extends DefaultJsonProtocol {

  implicit object StringSeqMappingFormat extends JsonFormat[() => Map[String, Seq[String]]] {
    override def read(json: JsValue): () => Map[String, Seq[String]] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "FROM_JSON" =>
          new SerializableSupplier[Map[String, Seq[String]]]() {
            override def apply(): Map[String, Seq[String]] = fields("value").convertTo[Map[String, Seq[String]]]
          }
        case "FROM_JSON_FILE" =>
          val file = fields("file").convertTo[String]
          val persistenceModule = wire[PersistenceModule]
          val source: Source = persistenceModule.persistenceDIModule.fileReader.getSource(file)
          val mapping = source.getLines().mkString("\n").parseJson.convertTo[Map[String, Seq[String]]]
          new SerializableSupplier[Map[String, Seq[String]]]() {
            override def apply(): Map[String, Seq[String]] = mapping
          }
      }
      case e => throw DeserializationException(s"Expected a valid value vor () => Map[String, Seq[String]] but got value $e")
    }

    override def write(obj: () => Map[String, Seq[String]]): JsValue = """{}""".toJson
  }


}
