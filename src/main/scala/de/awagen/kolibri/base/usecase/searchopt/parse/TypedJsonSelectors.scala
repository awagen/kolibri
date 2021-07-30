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


package de.awagen.kolibri.base.usecase.searchopt.parse

import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors.{JsValueSeqSelector, PlainSelector}
import de.awagen.kolibri.datatypes.NamedClassTyped
import play.api.libs.json.{JsValue, Reads}


/**
  * Selectors that combine the actual selection on JsValue with a typed and named key
  */
object TypedJsonSelectors {

  trait Selector[T] {
    def select(jsValue: JsValue): T
  }

  trait SingleValueSelector[T] extends Selector[Option[T]] {
    def select(jsValue: JsValue): Option[T]
  }

  trait SeqSelector[T] extends Selector[scala.collection.Seq[T]] {
    def select(jsValue: JsValue): scala.collection.Seq[T]
  }

  case class TypedJsonSeqSelector[T](selector: JsValueSeqSelector, namedClassTyped: NamedClassTyped[T])(implicit reads: Reads[T]) extends SeqSelector[T] {
    def select(jsValue: JsValue): scala.collection.Seq[T] = selector.select(jsValue).map(x => x.as[T]).toSeq
  }

  case class TypedJsonSingleValueSelector[T](selector: PlainSelector, namedClassTyped: NamedClassTyped[T])(implicit reads: Reads[T]) extends SingleValueSelector[T] {
    def select(jsValue: JsValue): Option[T] = selector.select(jsValue).toOption.map(x => x.as[T])
  }

}