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

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.Corn
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.base.io.json.TaggingConfigurationsJsonProtocol.{MapKeyTaggerFuncFormat, MetricRowTaggerFuncFormat, RequestTemplateTaggerFuncFormat, taggingConfigurationJsonFormat}
import de.awagen.kolibri.base.processing.tagging.TaggingConfigurations.{BaseTaggingConfiguration, TaggedMetricRowStore, TaggedRequestTemplateStore, EitherThrowableOrTaggedWeaklyTypedMapStore, TaggingConfiguration}
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.TagType.AGGREGATION
import de.awagen.kolibri.datatypes.tagging.Tags
import de.awagen.kolibri.datatypes.tagging.Tags.{ParameterMultiValueTag, StringTag}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableConsumer
import spray.json._

import scala.collection.mutable

class TaggingConfigurationsJsonProtocolSpec extends UnitTestSpec {

  val requestTemplateTaggerJson: JsValue =
    """
      |{
      |"type": "REQUEST_PARAMETER",
      |"parameter": "param1",
      |"extend": false
      |}
      |""".stripMargin.parseJson

  val mapKeyTaggerJson: JsValue =
    """
      |{
      |"type": "LENGTH",
      |"key": "seq_key",
      |"extend": false
      |}
      |""".stripMargin.parseJson

  val metricRowTaggerJson: JsValue =
    """
      |{
      |"type": "NOTHING"
      |}
      |""".stripMargin.parseJson

  val baseTaggingConfiguration: JsValue =
    """{
      | "initTagger": {
      |   "type": "REQUEST_PARAMETER",
      |   "parameter": "param1",
      |   "extend": false
      | },
      | "processedTagger": {
      |   "type": "LENGTH",
      |   "key": "seq_key",
      |   "extend": false
      | },
      | "resultTagger": {
      |   "type": "NOTHING"
      | }
      |}
      |""".stripMargin.parseJson

  "TaggingConfigurations" should {

    "correctly parse RequestTemplate by param tagger" in {
      // given
      val reqTemplate: RequestTemplate = RequestTemplate.apply("testpath",
        Map("t" -> Seq("a1"), "param1" -> Seq("v1")), Seq.empty)
      val processingMessage = Corn(reqTemplate)
      // when
      val reqTemplateTagger = requestTemplateTaggerJson.convertTo[SerializableConsumer[TaggedRequestTemplateStore]]
      reqTemplateTagger.apply(processingMessage)
      // then
      val tags: Set[Tags.Tag] = processingMessage.getTagsForType(AGGREGATION)
      tags mustBe Set(ParameterMultiValueTag(Map("param1" -> Seq("v1"))))
    }

    "correctly parse tagger by map value from key" in {
      // given
      val weaklyTypedMap = (Right(BaseWeaklyTypedMap(mutable.Map("seq_key" -> Seq(1, 2, 3, 4)))), RequestTemplate.apply("/", Map.empty, Seq.empty))
      val processingMessage = Corn(weaklyTypedMap)
      // when
      val mapKeyTagger = mapKeyTaggerJson.convertTo[SerializableConsumer[EitherThrowableOrTaggedWeaklyTypedMapStore]]
      mapKeyTagger.apply(processingMessage)
      // then
      processingMessage.getTagsForType(AGGREGATION) mustBe Set(StringTag("seq_key-size=4"))
    }

    "correctly parse metric row tagger" in {
      // given, when
      val metricRowTagger = metricRowTaggerJson.convertTo[SerializableConsumer[TaggedMetricRowStore]]
      // then
      metricRowTagger.isInstanceOf[SerializableConsumer[TaggedMetricRowStore]] mustBe true
    }

    "correctly parse TaggingConfiguration" in {
      val taggingConfig: TaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow] =
        baseTaggingConfiguration.convertTo[BaseTaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]]
      taggingConfig.isInstanceOf[TaggingConfiguration[RequestTemplate, (Either[Throwable, WeaklyTypedMap[String]], RequestTemplate), MetricRow]] mustBe true
    }

  }

}
