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

package de.awagen.kolibri.datatypes.tagging

import de.awagen.kolibri.datatypes.io.KolibriSerializable

object Tags {

  trait Tag extends KolibriSerializable {

    def stringId: String

  }

  trait TypedTag[T] extends Tag {
    val value: T
  }

  trait ParameterTag extends Tag {
    def formattedParamHeaderAndValues(columnSeparator: String): (String, String) = (formattedParamHeader(columnSeparator), formattedParamValues(columnSeparator))

    def formattedParamHeader(columnSeparator: String): String

    def formattedParamValues(columnSeparator: String): String
  }


  case class ParameterMultiValueTag(value: Map[String, Seq[String]]) extends TypedTag[Map[String, Seq[String]]] with ParameterTag {

    override def formattedParamHeader(columnSeparator: String): String = {
      value.keys.toSeq.sorted.mkString(columnSeparator)
    }

    override def formattedParamValues(columnSeparator: String): String = {
      val sortedKeys: Seq[String] = value.keys.toSeq.sorted
      sortedKeys.map(x => s"${value(x).sorted.mkString("&")}").mkString(columnSeparator)
    }

    def valueSeqToSet[T](map: Map[String, Seq[T]]): Map[String, Set[T]] = {
      map.to(LazyList).map(x => (x._1, x._2.toSet)).toMap
    }

    override def stringId: String = {
      value.keys.toSeq.sorted.to(LazyList).map(x => {
        value(x).sorted.map(v => s"$x=$v").mkString("&")
      }).mkString("&")
    }
  }


  // maps are equal if they share the same mappings
  case class ParameterSingleValueTag(value: Map[String, String]) extends TypedTag[Map[String, String]] with ParameterTag {

    override def formattedParamHeader(columnSeparator: String): String = {
      value.keys.toSeq.sorted.mkString(columnSeparator)
    }

    override def formattedParamValues(columnSeparator: String): String = {
      val sortedKeys: Seq[String] = value.keys.toSeq.sorted
      sortedKeys.map(x => value(x)).mkString(columnSeparator)
    }

    override def stringId: String = value.keys.toSeq.sorted.to(LazyList).map(x => s"$x=${value(x)}").mkString("&")

  }

  // tag as used for aggregations of results
  case class AggregationTag(id: String, varParamTag: ParameterTag, fixedParamTag: ParameterTag) extends Tag {
    override def stringId: String = Seq("aggTag", id, varParamTag.stringId, fixedParamTag.stringId).mkString("-")
  }

  case class MultiTag(value: Set[Tag]) extends TypedTag[Set[Tag]] {

    def add(tag: Tag): MultiTag = MultiTag(value + tag)

    override def stringId: String = value.toSeq.map(x => x.stringId).sorted.mkString("-")
  }

  object StringTag {
    val ALL = StringTag("ALL")
  }

  case class StringTag(value: String) extends TypedTag[String] {
    override def stringId: String = value
  }

  case class NamedTag(name: String, tag: Tag) extends Tag {
    override def stringId: String = Seq(name, tag.stringId).mkString("-")
  }

  def tagToParameterHeader(tag: Tag, columnSeparator: String): String = tag match {
    case e: ParameterTag => e.formattedParamHeader(columnSeparator)
    case e: MultiTag => e.value.filter(x => x.isInstanceOf[ParameterTag]).head.asInstanceOf[ParameterTag].formattedParamHeader(columnSeparator)
    case _ => ""
  }

  def tagToParameterValues(tag: Tag, columnSeparator: String): String = tag match {
    case e: ParameterTag => e.formattedParamValues(columnSeparator)
    case e: MultiTag => e.value.filter(x => x.isInstanceOf[ParameterTag]).head.asInstanceOf[ParameterTag].formattedParamValues(columnSeparator)
    case _ => ""
  }

}
