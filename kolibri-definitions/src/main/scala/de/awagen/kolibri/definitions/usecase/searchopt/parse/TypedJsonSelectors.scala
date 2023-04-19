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


package de.awagen.kolibri.definitions.usecase.searchopt.parse

import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors.{JsValueSeqSelector, PlainSelector}
import de.awagen.kolibri.datatypes.types.JsonTypeCast.JsonTypeCast
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.types.{JsonTypeCast, NamedClassTyped}
import play.api.libs.json.JsValue


/**
  * Selectors that combine the actual selection on JsValue with a typed and named key
  */
object TypedJsonSelectors {

  trait Selector[+T] extends KolibriSerializable {
    def select(jsValue: JsValue): T
  }

  trait NamedAndTypedSelector[+T] extends KolibriSerializable with Selector[T] {
    def name: String
    def classTyped: NamedClassTyped[_]
  }

  case class TypedJsonSeqSelector(name: String, selector: JsValueSeqSelector, castType: JsonTypeCast) extends NamedAndTypedSelector[Seq[Any]] {
    // note that here we need to assume the SEQ version of the passed castType. Convention for the respective enum naming
    // is SEQ_ prepended to the type contained in the seq, e.g SEQ_STRING, SEQ_BOOLEAN, SEQ_DOUBLE, SEQ_FLOAT,... (all uppercase)
    // The passed castType is for single elements of the Seq
    def classTyped: NamedClassTyped[_] = JsonTypeCast.withName(s"SEQ_${castType.typeName.toUpperCase}").asInstanceOf[JsonTypeCast].toNamedClassType(name)

    def select(jsValue: JsValue): Seq[_] = selector.select(jsValue).map(x => castType.cast(x)).toSeq
  }

  case class TypedJsonSingleValueSelector(name: String, selector: PlainSelector, castType: JsonTypeCast) extends NamedAndTypedSelector[Option[Any]] {
    def classTyped: NamedClassTyped[_] = castType.toNamedClassType(name)

    def select(jsValue: JsValue): Option[_] = selector.select(jsValue).toOption.map(x => castType.cast(x))
  }

}