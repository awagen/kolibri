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

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.io.json.OrderedMultiValuesJsonProtocol._
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, RootJsonFormat, enrichAny}

object IndexedGeneratorJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {

  implicit object SeqMapIndexedGeneratorFormat extends RootJsonFormat[IndexedGenerator[Map[String, Seq[String]]]] {
    override def read(json: JsValue): IndexedGenerator[Map[String, Seq[String]]] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        // That case is unlikely, since in case single OrderedMultiValues contains several values for a key
        // all would be contained in the respective value of the map, which for many cases
        // would not be wanted. E.g doing this when generating ParamsMapper maps would lead
        // to all the values being set for a single parameter setting (e.g all values at once instead
        // of being a variant by themselves
        case "BY_MULTIVALUES" =>
          val paramMaps: Seq[Map[String, Seq[String]]] = fields("values").convertTo[Seq[OrderedMultiValues]]
            .map(x => x.values.map(v => v.name -> v.getAll.map(x => x.toString)).toMap)
          ByFunctionNrLimitedIndexedGenerator.createFromSeq(paramMaps)
        case "BY_MAPSEQ" =>
          val paramMaps: Seq[Map[String, Seq[String]]] = fields("values").convertTo[Seq[Map[String, Seq[String]]]]
          ByFunctionNrLimitedIndexedGenerator.createFromSeq(paramMaps)
      }
      case e => throw DeserializationException(s"Expected a value from IndexedGenerator[Map[String, Seq[String]]]  but got value $e")
    }

    override def write(obj: IndexedGenerator[Map[String, Seq[String]]]): JsValue = """{}""".toJson
  }

  implicit object MapIndexedGeneratorFormat extends RootJsonFormat[IndexedGenerator[Map[String, String]]] {
    override def read(json: JsValue): IndexedGenerator[Map[String, String]] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "BY_VALUES_SEQ" =>
          val params: Seq[Map[String, String]] = fields("values").convertTo[Seq[Map[String, String]]]
          ByFunctionNrLimitedIndexedGenerator.createFromSeq(params)
      }
      case e => throw DeserializationException(s"Expected a value from IndexedGenerator[Map[String, String]]  but got value $e")
    }

    override def write(obj: IndexedGenerator[Map[String, String]]): JsValue = """{}""".toJson
  }

  implicit object StringIndexedGeneratorFormat extends RootJsonFormat[IndexedGenerator[String]] {
    override def read(json: JsValue): IndexedGenerator[String] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "BY_VALUES_SEQ" =>
          val params: Seq[String] = fields("values").convertTo[Seq[String]]
          ByFunctionNrLimitedIndexedGenerator.createFromSeq(params)
        case "BY_FILENAME_KEYS" =>
          val directory: String = fields("directory").convertTo[String]
          val filesSuffix: String = fields("filesSuffix").convertTo[String]
          val directoryReader = AppConfig.persistenceModule.persistenceDIModule.dataOverviewReader(x => x.endsWith(filesSuffix))
          val keys: Seq[String] = directoryReader.listResources(directory, _ => true)
            .map(file => file.split("/").last.stripSuffix(filesSuffix))
          ByFunctionNrLimitedIndexedGenerator.createFromSeq(keys)
      }
      case e => throw DeserializationException(s"Expected a value from IndexedGenerator[String]  but got value $e")
    }

    override def write(obj: IndexedGenerator[String]): JsValue = """{}""".toJson
  }

  implicit object SeqStringIndexedGeneratorFormat extends RootJsonFormat[IndexedGenerator[Seq[String]]] {
    override def read(json: JsValue): IndexedGenerator[Seq[String]] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "BY_VALUES_SEQ" =>
          val params: Seq[Seq[String]] = fields("values").convertTo[Seq[Seq[String]]]
          ByFunctionNrLimitedIndexedGenerator.createFromSeq(params)
      }
      case e => throw DeserializationException(s"Expected a value from IndexedGenerator[String]  but got value $e")
    }

    override def write(obj: IndexedGenerator[Seq[String]]): JsValue = """{}""".toJson
  }

}
