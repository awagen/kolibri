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


package de.awagen.kolibri.definitions.io.json

import com.amazonaws.regions.Regions
import de.awagen.kolibri.storage.io.writer.Writers.{FileWriter, Writer}
import de.awagen.kolibri.storage.io.writer.aggregation.{BaseMetricAggregationWriter, BaseMetricDocumentWriter}
import de.awagen.kolibri.storage.io.writer.base.{AwsS3FileWriter, LocalDirectoryFileWriter}
import de.awagen.kolibri.datatypes.io.json.MetricDocumentFormatJsonProtocol._
import de.awagen.kolibri.datatypes.metrics.aggregation.mutable.MetricAggregation
import de.awagen.kolibri.datatypes.metrics.aggregation.writer.MetricDocumentFormat
import de.awagen.kolibri.datatypes.stores.mutable.MetricDocument
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat}


object WriterJsonProtocol extends DefaultJsonProtocol {

  val DIRECTORY_FIELD = "directory"
  val BUCKET_NAME_FIELD = "bucketName"
  val DIR_PATH_FIELD = "dirPath"
  val REGION_FIELD = "region"
  val CONTENT_TYPE_FIELD = "contentType"
  val WRITER_FIELD = "writer"
  val FORMATS_FIELD = "formats"
  val SUBFOLDER_FIELD = "subFolder"
  val PATH_SEPARATOR_FIELD = "pathSeparator"

  implicit object StringFileWriterFormat extends JsonFormat[FileWriter[String, Any]] {
    override def read(json: JsValue): FileWriter[String, Any] = json match {
      case spray.json.JsObject(fields) if fields.contains(DIRECTORY_FIELD) && fields.keys.size == 1 =>
        LocalDirectoryFileWriter(fields(DIRECTORY_FIELD).convertTo[String])
      case spray.json.JsObject(fields) if fields.contains(BUCKET_NAME_FIELD) &&
        fields.contains(DIR_PATH_FIELD) && fields.contains(REGION_FIELD) && fields.contains(CONTENT_TYPE_FIELD) &&
        fields.keys.size == 4 =>
        AwsS3FileWriter(fields(BUCKET_NAME_FIELD).convertTo[String],
          fields(DIR_PATH_FIELD).convertTo[String],
          Regions.valueOf(fields(REGION_FIELD).convertTo[String]),
          fields(CONTENT_TYPE_FIELD).convertTo[String])
      case e => throw DeserializationException(s"Expected a value from FileWriter[String, _] but got value $e")
    }

    override def write(obj: FileWriter[String, Any]): JsValue = JsString(obj.toString)
  }


  implicit object MetricDocumentWriterFormat extends JsonFormat[Writer[MetricDocument[Tag], Tag, Any]] {

    override def read(json: JsValue): Writer[MetricDocument[Tag], Tag, Any] = json match {
      case spray.json.JsObject(fields) if fields.contains(WRITER_FIELD) &&
        fields.contains(FORMATS_FIELD) && fields.contains(PATH_SEPARATOR_FIELD) =>
        BaseMetricDocumentWriter(
          fields(WRITER_FIELD).convertTo[FileWriter[String, Any]],
          fields(FORMATS_FIELD).convertTo[Seq[MetricDocumentFormat]],
          fields(SUBFOLDER_FIELD).convertTo[String],
          fields(PATH_SEPARATOR_FIELD).convertTo[String],
          x => x.toString)
      case e => throw DeserializationException(s"Expected a value from Writer[MetricDocument[Tag], Tag, Any] but got value $e")
    }

    override def write(obj: Writer[MetricDocument[Tag], Tag, Any]): JsValue = JsString(obj.toString)
  }


  implicit object MetricAggregationWriterFormat extends JsonFormat[Writer[MetricAggregation[Tag], Tag, Any]] {

    override def read(json: JsValue): Writer[MetricAggregation[Tag], Tag, Any] = json match {
      case spray.json.JsObject(fields) if fields.contains(WRITER_FIELD) =>
        BaseMetricAggregationWriter(
          fields(WRITER_FIELD).convertTo[Writer[MetricDocument[Tag], Tag, Any]])
      case e => throw DeserializationException(s"Expected a value from Writer[MetricAggregation[Tag], String, Any] but got value $e")
    }

    override def write(obj: Writer[MetricAggregation[Tag], Tag, Any]): JsValue = JsString(obj.toString)
  }


}
