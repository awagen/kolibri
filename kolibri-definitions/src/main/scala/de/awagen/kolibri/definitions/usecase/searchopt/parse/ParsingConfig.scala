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

import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors.NamedAndTypedSelector
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsValue

import scala.collection.mutable


object ParsingConfig {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)


}

/**
 * container to provide typed json selectors
 *
 * we are only referencing specific types here as this is the level on which the json protocols are available at the moment
 * after generalizing that, can switch to SingleValueSelector and SeqSelector
 *
 * @param singleSelectors - single value selector, e.g non-recursive
 * @param seqSelectors    - seq selectors, e.g recursive
 */
case class ParsingConfig(selectors: Seq[NamedAndTypedSelector[Any]]) {

  import ParsingConfig._

  def jsValueToTypeTaggedMap: SerializableFunction1[JsValue, WeaklyTypedMap[String]] = new SerializableFunction1[JsValue, WeaklyTypedMap[String]] {
    override def apply(jsValue: JsValue): WeaklyTypedMap[String] = {
      val typedMap = BaseWeaklyTypedMap(mutable.Map.empty)
      selectors.foreach(selector => {
        val value: Any = selector.select(jsValue)
        value match {
          case v: Option[_] => v.foreach(value => typedMap.put(selector.name, value))
          case _ => typedMap.put(selector.name, value)
        }

      })
      if (typedMap.keys.isEmpty) {
        logger.warn("no data placed in typed map")
      }
      typedMap
    }
  }

}
