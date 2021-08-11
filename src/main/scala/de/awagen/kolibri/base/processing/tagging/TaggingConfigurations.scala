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


package de.awagen.kolibri.base.processing.tagging

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.base.http.client.request.RequestTemplate
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.TagType.TagType
import de.awagen.kolibri.datatypes.tagging.Tags.{ParameterMultiValueTag, Tag}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableConsumer

object TaggingConfigurations {

  trait TaggingConfiguration[U, V, W] extends KolibriSerializable {

    def tagInit(tagged: ProcessingMessage[U]): Unit

    def tagProcessed(tagged: ProcessingMessage[V]): Unit

    def tagResult(tagged: ProcessingMessage[W]): Unit

  }

  case class BaseTaggingConfiguration[U, V, W](initTagger: SerializableConsumer[ProcessingMessage[U]],
                                               processedTagger: SerializableConsumer[ProcessingMessage[V]],
                                               resultTagger: SerializableConsumer[ProcessingMessage[W]]) extends TaggingConfiguration[U, V, W] {
    override def tagInit(tagged: ProcessingMessage[U]): Unit = initTagger.apply(tagged)

    override def tagProcessed(tagged: ProcessingMessage[V]): Unit = processedTagger.apply(tagged)

    override def tagResult(tagged: ProcessingMessage[W]): Unit = resultTagger.apply(tagged)
  }

  type TaggedRequestTemplateStore = ProcessingMessage[RequestTemplate]
  type TaggedWeaklyTypedMapStore = ProcessingMessage[WeaklyTypedMap[String]]
  type TaggedMetricRowStore = ProcessingMessage[MetricRow]

  /**
    * Extends the tags of the tagged instance with given tag for given tag type, for each already existing tag that
    * passes the filterFunc.
    * If extend is false, just adds the tag to the existing ones for the tagType, without applying the filterFunc
    *
    * @param paramName  - name of the parameter to extract from RequestTemplate
    * @param tagType    - the tag type under which the extracted parameter values shall be added as tag
    * @param filterFunc - in case extend is true, extends those tags for which filterFunc returns true. If extend is false, just adds tag to the existing ones.
    * @param extend     - determines whether the extracted parameter tag is added by just adding to existing (extend=false) or by extending the existing
    *                   ones passing filterFunc. This would extend a single value tag to a MultiTag and in case of MultiTag just add to it.
    * @return
    */
  def requestByParameterTagger(paramName: String, tagType: TagType, filterFunc: Tag => Boolean, extend: Boolean): SerializableConsumer[TaggedRequestTemplateStore] = new SerializableConsumer[TaggedRequestTemplateStore] {
    override def apply(v1: TaggedRequestTemplateStore): Unit = {
      val paramTag: Option[ParameterMultiValueTag] = v1.data.getParameter(paramName).map(params => ParameterMultiValueTag(Map(paramName -> params)))
      paramTag.foreach(tag => {
        if (extend) v1.extendAllWithTag(tagType, tag, filterFunc)
        else v1.addTag(tagType, tag)
      })
    }
  }

  /**
    * Given a WeaklyTypedMap[String], extract key referring to type T (will give None if type of value doesnt match type T),
    * use mapFunc to transform the value into a tag, and either add this tag to existing keys (extend = false) or
    * extend the existing ones that pass filterFunc (extend=true)
    *
    * @param key        - key to extract from WeaklyTypedMap
    * @param tagType    - tag type for which to add the extracted tag
    * @param mapFunc    - mapping the value extracted from the map to a tag
    * @param filterFunc - if applied to given tag determines whether the tag should be extended (will be extended if function yields true),
    *                   only with effect if extended=true
    * @param extend     - if set to false, will just add the tag to existing ones, otherwise will add the new tag to each tag that
    *                   yields true on filterFunc
    * @tparam T - the expected type of the value for the given key
    * @return
    */
  def valueByKeyAndTypeTagger[T](key: String, tagType: TagType, mapFunc: T => Tag, filterFunc: Tag => Boolean, extend: Boolean): SerializableConsumer[TaggedWeaklyTypedMapStore] = new SerializableConsumer[TaggedWeaklyTypedMapStore] {
    override def apply(v1: TaggedWeaklyTypedMapStore): Unit = {
      val value: Option[T] = v1.data.get[T](key)
      value.map(x => mapFunc.apply(x)).foreach(tag => {
        if (extend) v1.extendAllWithTag(tagType, tag, filterFunc)
        else v1.addTag(tagType, tag)
      })
    }
  }

  /**
    * Tagger for tagged data store containing data of type MetricRow.
    *
    * @param mapFunc
    * @param filterFunc
    * @param extend
    * @return
    */
  def metricRowTagger(mapFunc: MetricRow => Option[Tag], filterFunc: Tag => Boolean, extend: Boolean): SerializableConsumer[TaggedMetricRowStore] = new SerializableConsumer[TaggedMetricRowStore] {
    override def apply(v1: TaggedMetricRowStore): Unit = {
      val tag: Option[Tag] = mapFunc.apply(v1.data)
      tag.foreach(t => {
        if (extend) t.extendAllWithTag(t, filterFunc)
        else t.addTag(t)
      })
    }
  }

}
