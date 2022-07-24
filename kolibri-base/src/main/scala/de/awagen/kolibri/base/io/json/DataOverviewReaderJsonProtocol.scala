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
import de.awagen.kolibri.base.io.reader.{AwsS3DirectoryReader, DataOverviewReader, LocalDirectoryReader, LocalResourceDirectoryReader}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, enrichAny}

import scala.util.matching.Regex

object DataOverviewReaderJsonProtocol extends DefaultJsonProtocol {

  val CLASSPATH_REGEX_FILEFILTER_TYPE = "CLASSPATH_REGEX_FILEFILTER"
  val LOCAL_READER_TYPE = "LOCAL_READER"
  val AWS_READER_TYPE = "AWS_READER"
  val TYPE_KEY = "type"
  val BASE_DIR_KEY = "baseDir"
  val REGEX_KEY = "regex"
  val ENCODING_KEY = "encoding"
  val UTF_8_VALUE = "UTF-8"
  val BUCKET_NAME_KEY = "bucketName"
  val DIR_PATH_KEY = "dirPath"
  val REGION_KEY = "region"
  val DELIMITER_KEY = "delimiter"

  implicit object DataOverviewReaderFormat extends JsonFormat[DataOverviewReader] {
    override def read(json: JsValue): DataOverviewReader = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case CLASSPATH_REGEX_FILEFILTER_TYPE =>
          val baseDir: String = fields(BASE_DIR_KEY).convertTo[String]
          val regex: Regex = fields(REGEX_KEY).convertTo[String].r
          val encoding: String = fields.get(ENCODING_KEY).map(x => x.convertTo[String]).getOrElse(UTF_8_VALUE)
          val baseFilenameFilter: String => Boolean = new SerializableFunction1[String, Boolean] {
            override def apply(filename: String): Boolean = regex.matches(filename)
          }
          LocalResourceDirectoryReader(baseDir, baseFilenameFilter, encoding)
        case LOCAL_READER_TYPE =>
          val baseDir: String = fields(BASE_DIR_KEY).convertTo[String]
          val regex: Regex = fields(REGEX_KEY).convertTo[String].r
          val encoding: String = fields.get(ENCODING_KEY).map(x => x.convertTo[String]).getOrElse(UTF_8_VALUE)
          val baseFilenameFilter: String => Boolean = new SerializableFunction1[String, Boolean] {
            override def apply(filename: String): Boolean = regex.matches(filename)
          }
          LocalDirectoryReader(baseDir, baseFilenameFilter, encoding)
        case AWS_READER_TYPE =>
          val bucketName: String = fields(BUCKET_NAME_KEY).convertTo[String]
          val dirPath: String = fields(DIR_PATH_KEY).convertTo[String]
          val regionStr: String = fields(REGION_KEY).convertTo[String]
          val regions: Regions = Regions.valueOf(regionStr)
          val delimiter: String = fields(DELIMITER_KEY).convertTo[String]
          val regex: Regex = fields(REGEX_KEY).convertTo[String].r
          val baseFilenameFilter: String => Boolean = new SerializableFunction1[String, Boolean] {
            override def apply(filename: String): Boolean = regex.matches(filename)
          }
          AwsS3DirectoryReader(bucketName, dirPath, regions, delimiter,
            baseFilenameFilter)

      }
    }

    override def write(obj: DataOverviewReader): JsValue = """{}""".toJson
  }

}
