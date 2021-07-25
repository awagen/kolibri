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

import play.api.libs.json.{JsDefined, JsLookupResult, JsReadable, JsUndefined, JsValue}


object JsonSelectors {

  /**
    * Single key selector. Keeps JsLookupResult as outcome that can either
    * be JsDefined or JsUndefined, as some kind of Option equivalent
    * for selections
    *
    * @param key
    */
  case class SingleKeySelector(key: String) {
    def select(jsValue: JsValue): JsLookupResult = jsValue \ key

    def select(jsValue: JsLookupResult): JsLookupResult = jsValue \ key
  }

  /**
    * Utilizes recursive selector, e.g in case values for a single key
    * shall be selected from an array of json values.
    *
    * @param key - key to collect values for
    */
  case class RecursiveSelector(key: String) {
    def select(jsValue: JsValue): collection.Seq[JsValue] = jsValue \\ key

    def select(lookupResult: JsLookupResult): collection.Seq[JsValue] = lookupResult \\ key
  }

  /**
    * Passing a sequence of selectors, pick up the value that corresponds
    * to this selector path
    *
    * @param selectorKeys - sequential keys
    */
  case class PlainPathSelector(selectorKeys: Seq[String]) {

    val selectors: Seq[SingleKeySelector] = selectorKeys.map(x => SingleKeySelector(x))

    def select(jsValue: JsValue): JsLookupResult = {
      var currentLookupOpt = Option.empty[JsLookupResult]
      selectors.foreach(selector => {
        if (currentLookupOpt.isEmpty) currentLookupOpt = Some(selector.select(jsValue))
        else {
          currentLookupOpt = currentLookupOpt.map(x => selector.select(x))
        }
      })
      currentLookupOpt.getOrElse(JsUndefined("selection not found"))
    }

  }

  /**
    * Cobining plain path selector followed by a recursive selector
    *
    * @param recursiveSelectorKey - the key for the recursive selector
    * @param plainSelectorKeys    - the keys for the plain path query before applying the recursive selector
    */
  case class PlainAndRecursiveSelector(recursiveSelectorKey: String, plainSelectorKeys: String*) {
    def select(jsValue: JsValue): collection.Seq[JsValue] = {
      var lookupResult: JsLookupResult = JsDefined(jsValue)
      if (plainSelectorKeys.nonEmpty) {
        val plainSelector = PlainPathSelector(plainSelectorKeys)
        lookupResult = plainSelector.select(jsValue)
      }
      val recursiveSelector = RecursiveSelector(recursiveSelectorKey)
      recursiveSelector.select(lookupResult)
    }
  }

  /**
    * Enum reflecting the distinct type conversions
    */
  object ResultTypeEnum extends Enumeration {
    type ResultTypeEnum = Val[_]

    case class Val[T](parseFunc: JsReadable => T) extends super.Val

    val STRING: Val[String] = Val(x => x.as[String])
    val DOUBLE: Val[Double] = Val(x => x.as[Double])

  }

  /**
    * Selector config, allowing combination of plain path selectors and
    * recursive selector. Defines the result type as well to be used in
    * the result conversion.
    *
    * @param plainSelectorKeys    - seq of plain selector keys, can be empty
    * @param recursiveSelectorKey - optional key for recursive selector
    * @param singleResultType     - result type of the expected result, used to apply
    *                             type conversion on the result
    */
  case class SelectorConfig(plainSelectorKeys: Seq[String],
                            recursiveSelectorKey: Option[String],
                            singleResultType: ResultTypeEnum.Val[_]) {

  }

}
