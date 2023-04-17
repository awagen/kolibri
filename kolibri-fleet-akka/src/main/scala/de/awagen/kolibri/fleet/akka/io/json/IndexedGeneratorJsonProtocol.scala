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


package de.awagen.kolibri.fleet.akka.io.json

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.awagen.kolibri.base.io.json.IndexedGeneratorJsonProtocol.{BY_FILENAME_KEYS_TYPE, BY_VALUES_SEQ_TYPE, DIRECTORY_KEY, DIRECTORY_SEPARATOR, FILES_SUFFIX_KEY, TYPE_KEY, VALUES_KEY}
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.fleet.akka.config.AppConfig
import spray.json.{DefaultJsonProtocol, DeserializationException, JsValue, RootJsonFormat, enrichAny}

object IndexedGeneratorJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {

  implicit object StringIndexedGeneratorFormat extends RootJsonFormat[IndexedGenerator[String]] {
    override def read(json: JsValue): IndexedGenerator[String] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case BY_VALUES_SEQ_TYPE =>
          val params: Seq[String] = fields(VALUES_KEY).convertTo[Seq[String]]
          ByFunctionNrLimitedIndexedGenerator.createFromSeq(params)
        // this would create a generator over the names in the passed folder with given suffix
        // not the full path but just the filename. It would not not extract any values from the files though
        case BY_FILENAME_KEYS_TYPE =>
          val directory: String = fields(DIRECTORY_KEY).convertTo[String]
          val filesSuffix: String = fields(FILES_SUFFIX_KEY).convertTo[String]
          val directoryReader = AppConfig.persistenceModule.persistenceDIModule.dataOverviewReader(x => x.endsWith(filesSuffix))
          val keys: Seq[String] = directoryReader.listResources(directory, _ => true)
            .map(file => file.split(DIRECTORY_SEPARATOR).last.stripSuffix(filesSuffix))
          ByFunctionNrLimitedIndexedGenerator.createFromSeq(keys)
      }
      case e => throw DeserializationException(s"Expected a value from IndexedGenerator[String]  but got value $e")
    }

    override def write(obj: IndexedGenerator[String]): JsValue = """{}""".toJson
  }

}
