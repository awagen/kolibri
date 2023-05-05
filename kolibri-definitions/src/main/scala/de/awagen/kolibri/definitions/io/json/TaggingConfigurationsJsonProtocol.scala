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

import de.awagen.kolibri.definitions.http.client.request.RequestTemplate
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.tagging.TaggingConfigurations
import de.awagen.kolibri.definitions.processing.tagging.TaggingConfigurations._
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.FieldDefinitions.FieldDef
import de.awagen.kolibri.datatypes.types.JsonStructDefs._
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableConsumer, SerializableFunction1}
import de.awagen.kolibri.datatypes.types.{JsonStructDefs, WithStructDef}
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, RootJsonFormat, enrichAny}

object TaggingConfigurationsJsonProtocol extends DefaultJsonProtocol {

  val TYPE_KEY = "type"
  val KEY_KEY = "key"
  val PARAMETER_KEY = "parameter"
  val EXTEND_KEY = "extend"
  val INIT_TAGGER_KEY = "initTagger"
  val PROCESSED_TAGGER_KEY = "processedTagger"
  val RESULT_TAGGER_KEY = "resultTagger"
  val REQUEST_PARAMETER_TYPE = "REQUEST_PARAMETER"
  val LENGTH_TYPE = "LENGTH"
  val NOTHING_TYPE = "NOTHING"

  implicit object RequestTemplateTaggerFuncFormat extends JsonFormat[SerializableConsumer[TaggedRequestTemplateStore]] with WithStructDef {
    override def read(json: JsValue): SerializableConsumer[TaggedRequestTemplateStore] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case REQUEST_PARAMETER_TYPE =>
          val parameterName = fields(PARAMETER_KEY).convertTo[String]
          val extend = fields(EXTEND_KEY).convertTo[Boolean]
          val filterFunc = new SerializableFunction1[Tag, Boolean] {
            override def apply(v1: Tag): Boolean = true
          }
          TaggingConfigurations.requestByParameterTagger(parameterName, AGGREGATION, filterFunc, extend)
      }
    }

    // TODO
    override def write(obj: SerializableConsumer[TaggedRequestTemplateStore]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(StringConstantStructDef(TYPE_KEY), StringChoiceStructDef(Seq(REQUEST_PARAMETER_TYPE)), required = true),
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              REQUEST_PARAMETER_TYPE -> Seq(
                FieldDef(StringConstantStructDef(PARAMETER_KEY), RegexStructDef(".+".r), required = true),
                FieldDef(StringConstantStructDef(EXTEND_KEY), BooleanStructDef, required = true),
              )
            )
          )
        )
      )
    }
  }

  implicit object MapKeyTaggerFuncFormat extends JsonFormat[SerializableConsumer[EitherThrowableOrTaggedWeaklyTypedMapStore]] with WithStructDef {
    override def read(json: JsValue): SerializableConsumer[EitherThrowableOrTaggedWeaklyTypedMapStore] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case LENGTH_TYPE =>
          val key = fields(KEY_KEY).convertTo[String]
          val mapFunc = new SerializableFunction1[Seq[Any], Tag] {
            override def apply(v1: Seq[Any]): Tag = StringTag(s"$key-size=${v1.size}")
          }
          val filterFunc = new SerializableFunction1[Tag, Boolean] {
            override def apply(v1: Tag): Boolean = true
          }
          val extend = fields(EXTEND_KEY).convertTo[Boolean]
          valueByKeyAndTypeTagger[Seq[Any]](key, AGGREGATION, mapFunc, filterFunc, extend)
        case NOTHING_TYPE => new SerializableConsumer[EitherThrowableOrTaggedWeaklyTypedMapStore] {
          override def apply(v1: EitherThrowableOrTaggedWeaklyTypedMapStore): Unit = ()
        }
      }
    }

    // TODO
    override def write(obj: SerializableConsumer[EitherThrowableOrTaggedWeaklyTypedMapStore]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              LENGTH_TYPE,
              NOTHING_TYPE
            )),
            required = true
          ),
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              LENGTH_TYPE -> Seq(
                FieldDef(StringConstantStructDef(KEY_KEY), RegexStructDef(".+".r), required = true),
                FieldDef(StringConstantStructDef(EXTEND_KEY), BooleanStructDef, required = true),
              ),
              NOTHING_TYPE -> Seq.empty
            )
          )
        )
      )
    }
  }

  implicit object MetricRowTaggerFuncFormat extends JsonFormat[SerializableConsumer[TaggedMetricRowStore]] with WithStructDef {
    override def read(json: JsValue): SerializableConsumer[TaggedMetricRowStore] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case NOTHING_TYPE => new SerializableConsumer[TaggedMetricRowStore] {
          override def apply(v1: TaggedMetricRowStore): Unit = ()
        }
      }
    }

    // TODO
    override def write(obj: SerializableConsumer[TaggedMetricRowStore]): JsValue = """{}""".toJson

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(TYPE_KEY),
            StringChoiceStructDef(Seq(
              NOTHING_TYPE
            )),
            required = true
          ),
        ),
        Seq(
          ConditionalFields(
            TYPE_KEY,
            Map(
              NOTHING_TYPE -> Seq.empty
            )
          )
        )
      )
    }
  }


  implicit object TaggingConfigurationJsonFormat extends RootJsonFormat[BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]] with WithStructDef {

    val baseFormat = taggingConfigurationJsonFormat

    override def read(json: JsValue): BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow] =
      baseFormat.read(json)

    override def write(obj: BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]): JsValue =
      baseFormat.write(obj)

    override def structDef: JsonStructDefs.StructDef[_] = {
      NestedFieldSeqStructDef(
        Seq(
          FieldDef(
            StringConstantStructDef(INIT_TAGGER_KEY),
            RequestTemplateTaggerFuncFormat.structDef,
            required = true
          ),
          FieldDef(
            StringConstantStructDef(PROCESSED_TAGGER_KEY),
            MapKeyTaggerFuncFormat.structDef,
            required = true
          ),
          FieldDef(
            StringConstantStructDef(RESULT_TAGGER_KEY),
            MetricRowTaggerFuncFormat.structDef,
            required = true
          )
        ),
        Seq.empty
      )
    }
  }

  implicit val taggingConfigurationJsonFormat: RootJsonFormat[BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]] = jsonFormat(
    (initTagger: SerializableConsumer[ProcessingMessage[RequestTemplate]],
     processedTagger: SerializableConsumer[ProcessingMessage[(Either[Throwable, WeaklyTypedMap[String]], RequestTemplate)]],
     resultTagger: SerializableConsumer[ProcessingMessage[MetricRow]]) =>
      BaseTaggingConfiguration.apply(initTagger, processedTagger, resultTagger),
    INIT_TAGGER_KEY,
    PROCESSED_TAGGER_KEY,
    RESULT_TAGGER_KEY
  )

}
