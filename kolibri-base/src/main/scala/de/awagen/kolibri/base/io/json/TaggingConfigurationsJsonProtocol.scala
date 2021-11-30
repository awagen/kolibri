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

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.processing.tagging.TaggingConfigurations
import de.awagen.kolibri.base.processing.tagging.TaggingConfigurations._
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableConsumer, SerializableFunction1}
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, RootJsonFormat, enrichAny}

object TaggingConfigurationsJsonProtocol extends DefaultJsonProtocol {

  implicit object RequestTemplateTaggerFuncFormat extends JsonFormat[SerializableConsumer[TaggedRequestTemplateStore]] {
    override def read(json: JsValue): SerializableConsumer[TaggedRequestTemplateStore] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "REQUEST_PARAMETER" =>
          val parameterName = fields("parameter").convertTo[String]
          val extend = fields("extend").convertTo[Boolean]
          val filterFunc = new SerializableFunction1[Tag, Boolean] {
            override def apply(v1: Tag): Boolean = true
          }
          TaggingConfigurations.requestByParameterTagger(parameterName, AGGREGATION, filterFunc, extend)
      }
    }

    // TODO
    override def write(obj: SerializableConsumer[TaggedRequestTemplateStore]): JsValue = """{}""".toJson
  }

  implicit object MapKeyTaggerFuncFormat extends JsonFormat[SerializableConsumer[EitherThrowableOrTaggedWeaklyTypedMapStore]] {
    override def read(json: JsValue): SerializableConsumer[EitherThrowableOrTaggedWeaklyTypedMapStore] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "LENGTH" =>
          val key = fields("key").convertTo[String]
          val mapFunc = new SerializableFunction1[Seq[Any], Tag] {
            override def apply(v1: Seq[Any]): Tag = StringTag(s"$key-size=${v1.size}")
          }
          val filterFunc = new SerializableFunction1[Tag, Boolean] {
            override def apply(v1: Tag): Boolean = true
          }
          val extend = fields("extend").convertTo[Boolean]
          valueByKeyAndTypeTagger[Seq[Any]](key, AGGREGATION, mapFunc, filterFunc, extend)
        case "NOTHING" => new SerializableConsumer[EitherThrowableOrTaggedWeaklyTypedMapStore] {
          override def apply(v1: EitherThrowableOrTaggedWeaklyTypedMapStore): Unit = ()
        }
      }
    }

    // TODO
    override def write(obj: SerializableConsumer[EitherThrowableOrTaggedWeaklyTypedMapStore]): JsValue = """{}""".toJson
  }

  implicit object MetricRowTaggerFuncFormat extends JsonFormat[SerializableConsumer[TaggedMetricRowStore]] {
    override def read(json: JsValue): SerializableConsumer[TaggedMetricRowStore] = json match {
      case spray.json.JsObject(fields) => fields("type").convertTo[String] match {
        case "NOTHING" => new SerializableConsumer[TaggedMetricRowStore] {
          override def apply(v1: TaggedMetricRowStore): Unit = ()
        }
      }
    }

    // TODO
    override def write(obj: SerializableConsumer[TaggedMetricRowStore]): JsValue = """{}""".toJson
  }


  implicit val taggingConfigurationJsonFormat: RootJsonFormat[BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]] = jsonFormat(
    (initTagger: SerializableConsumer[ProcessingMessage[RequestTemplate]],
     processedTagger: SerializableConsumer[ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)]],
     resultTagger: SerializableConsumer[ProcessingMessage[MetricRow]]) =>
      BaseTaggingConfiguration.apply(initTagger, processedTagger, resultTagger),
    "initTagger",
    "processedTagger",
    "resultTagger"
  )

}
