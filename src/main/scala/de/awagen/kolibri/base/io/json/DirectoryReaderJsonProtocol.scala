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

import com.amazonaws.regions.Regions
import de.awagen.kolibri.base.io.reader.{AwsS3DirectoryReader, DirectoryReader, LocalDirectoryReader, LocalResourceDirectoryReader}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}

import scala.util.matching.Regex

object DirectoryReaderJsonProtocol extends DefaultJsonProtocol {

  implicit object DirectoryReaderFormat extends JsonFormat[DirectoryReader] {
    override def read(json: JsValue): DirectoryReader = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "CLASSPATH_REGEX_FILEFILTER" =>
          val baseDir: String = fields("baseDir").convertTo[String]
          val regex: Regex = fields("regex").convertTo[String].r
          val encoding: String = fields.get("encoding").map(x => x.convertTo[String]).getOrElse("UTF-8")
          val baseFilenameFilter: String => Boolean = new SerializableFunction1[String, Boolean] {
            override def apply(filename: String): Boolean = regex.matches(filename)
          }
          LocalResourceDirectoryReader(baseDir, baseFilenameFilter, encoding)
        case "LOCAL_READER" =>
          val baseDir: String = fields("baseDir").convertTo[String]
          val regex: Regex = fields("regex").convertTo[String].r
          val encoding: String = fields.get("encoding").map(x => x.convertTo[String]).getOrElse("UTF-8")
          val baseFilenameFilter: String => Boolean = new SerializableFunction1[String, Boolean] {
            override def apply(filename: String): Boolean = regex.matches(filename)
          }
          LocalDirectoryReader(baseDir, baseFilenameFilter, encoding)
        case "AWS_READER" =>
          val bucketName: String = fields("bucketName").convertTo[String]
          val dirPath: String = fields("dirPath").convertTo[String]
          val regionStr: String = fields("region").convertTo[String]
          val regions: Regions = Regions.valueOf(regionStr)
          val delimiter: String = fields("delimiter").convertTo[String]
          val regex: Regex = fields("regex").convertTo[String].r
          val baseFilenameFilter: String => Boolean = new SerializableFunction1[String, Boolean] {
            override def apply(filename: String): Boolean = regex.matches(filename)
          }
          AwsS3DirectoryReader(bucketName, dirPath, regions, delimiter,
            baseFilenameFilter)

      }
    }

    override def write(obj: DirectoryReader): JsValue = """{}""".toJson
  }

}
