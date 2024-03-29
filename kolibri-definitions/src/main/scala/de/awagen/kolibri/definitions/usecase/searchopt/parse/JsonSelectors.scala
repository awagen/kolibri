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

import de.awagen.kolibri.definitions.usecase.searchopt.io.json.JsonSelectorJsonProtocol.{PLAINREC_TYPE, PLAIN_SELECTOR_PLAIN_PATH_TYPE, RECPLAIN_TYPE, RECREC_TYPE, SINGLEREC_TYPE}
import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors.JsonSelectorPathRegularExpressions.{plainPathKeyGroupingRegex, recursivePathKeyGroupingRegex}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import play.api.libs.json._

import scala.util.matching.Regex


/**
 * Provides objects that represent json path selections. This includes single selections as well as
 * recursive selections, further either path or recursive selections applied to recursive selections,
 * to be able to dive into the depth needed.
 * Note: instead of distinct types and combinations of selectors
 * we could utilize a single state machine parser, just looking at the sequence on the fly
 * and applying mappings. Out of the box the play json api does not offer to add selectors
 * after a recursive "\\" selector, while here we want to be able to map further
 */
object JsonSelectors {

  object JsonSelectorPathRegularExpressions {

    /**
     * regex for single elements in the form "\ key1 \ key2 \ key3 ...",
     * yielding sequential key, here: Seq("key1", "key2", "ley3")
     */
    val plainPathKeyGroupingRegex: Regex = """^\\\s+(\w+)((?:\s+\\\s+\w+)*)$""".r

    /**
     * regex for recursive elements in the form "\ key1 \ key2 \\ key3 ..."
     * yielding sequential key, here: Seq("key1", "key2", "ley3"). Assumes arbitrary number
     * of single keys prepended by "\ " followed by final recursive key prepended with "\\ "
     * (corresponds to SINGLEREC / PLAINREC type)
     */
    val recursivePathKeyGroupingRegex: Regex = """^((?:\s*\\\s+\w+)*)\s*\\\\\s+(\w+)$""".r

    /**
     * corresponds to RECPLAIN type (e.g recursive followed by plain)
     * adjusted grouping such that only split into the two groups that make it
     */
    val recursivePlainPathKeyGroupingRegex: Regex = """^((?:\s*\\\s+\w+)*\s*\\\\\s+\w+)\s+(\\\s+\w+(?:\s+\\\s+\w+)*)$""".r

    /**
     * corresponds to RECREC type (e.g recursive followed by recursive, two consecutive PLAINREC)
     * adjusted grouping such that only split into the two groups that make it
     */
    val recursiveRecursivePathKeyGroupingRegex: Regex = """^((?:\s*\\\s+\w+)*\s*\\\\\s+\w+)((?:\s*\\\s+\w+)*\s*\\\\\s+\w+)$""".r

    val anyValidSelectorRegex: Regex = s"""($plainPathKeyGroupingRegex)|($recursivePathKeyGroupingRegex)|($recursivePlainPathKeyGroupingRegex)|($recursiveRecursivePathKeyGroupingRegex)""".r

    def findPathType(path: String): Option[String] = path match {
      case e if plainPathKeyGroupingRegex.matches(e) => Some(PLAIN_SELECTOR_PLAIN_PATH_TYPE)
      case e if recursivePathKeyGroupingRegex.matches(e) =>
        val subgroups = recursivePathKeyGroupingRegex.findAllIn(e).subgroups
          .filter(x => x.nonEmpty)
        // if only one group, its SINGLEREC, if multiple groups, its PLAINREC
        if (subgroups.size > 1) Some(PLAINREC_TYPE) else Some(SINGLEREC_TYPE)
      case e if recursivePlainPathKeyGroupingRegex.matches(e) => Some(RECPLAIN_TYPE)
      case e if recursiveRecursivePathKeyGroupingRegex.matches(e) => Some(RECREC_TYPE)
      case _ => None
    }

    def recursiveAndPlainSelectorKeysToSelector(recursiveSelectorKeys: Seq[String], plainSelectorKeys: Seq[String]): JsValueSeqSelector = {
      val recursive = PlainAndRecursiveSelector(recursiveSelectorKeys.last, recursiveSelectorKeys.slice(0, recursiveSelectorKeys.length - 1): _*)
      val plain = PlainPathSelector(plainSelectorKeys)
      RecursiveAndPlainSelector(recursive, plain)
    }

    def recursiveAndRecursiveSelectorKeysToSelector(recursiveSelectorKeys1: Seq[String], recursiveSelectorKeys2: Seq[String]): JsValueSeqSelector = {
      val recursive1 = PlainAndRecursiveSelector(recursiveSelectorKeys1.last, recursiveSelectorKeys1.slice(0, recursiveSelectorKeys1.length - 1): _*)
      val recursive2 = PlainAndRecursiveSelector(recursiveSelectorKeys2.last, recursiveSelectorKeys2.slice(0, recursiveSelectorKeys2.length - 1): _*)
      RecursiveAndRecursiveSelector(recursive1, recursive2)
    }

    /**
     * transforms path (single key prefixed with "\ ", recursive one with "\\ ") to respective selector
     * @param path
     * @return
     */
    def pathToSelector(path: String): Option[Selector[_]] = path match {
      case e if plainPathKeyGroupingRegex.matches(e) =>
        Some(JsonSelectors.pathToPlainSelector(e))
      case e if recursivePathKeyGroupingRegex.matches(e) =>
        val selectorKeys: Seq[String] = JsonSelectors.findRecursivePathKeys(path)
        // if only one group, its SINGLEREC, if multiple groups, its PLAINREC
        if (selectorKeys.size == 1) Some(RecursiveSelector(selectorKeys.head))
        else Some(PlainAndRecursiveSelector(selectorKeys.last, selectorKeys.slice(0, selectorKeys.length - 1): _*))
      case e if recursivePlainPathKeyGroupingRegex.matches(e) =>
        // in the subgroups, should contain of two selectors, first the recursive, the other one the plain
        val subgroups = recursivePlainPathKeyGroupingRegex.findAllIn(e).subgroups
          .filter(x => x.nonEmpty)
        if (subgroups.size != 2) {
          throw new RuntimeException("Selector should be recursive selector followed by plain path selector, but found" +
            "less than two groups")
        }
        val recSelectorKeys: Seq[String] = JsonSelectors.findRecursivePathKeys(subgroups.head)
        val plainSelectorKeys: Seq[String] = JsonSelectors.findPlainPathKeys(subgroups(1))
        Some(recursiveAndPlainSelectorKeysToSelector(recSelectorKeys, plainSelectorKeys))
      case e if recursiveRecursivePathKeyGroupingRegex.matches(e) =>
        val subgroups = recursiveRecursivePathKeyGroupingRegex.findAllIn(e).subgroups
          .filter(x => x.nonEmpty)
        if (subgroups.size != 2) {
          throw new RuntimeException("Selector should be recursive selector followed by recursive selector, but found" +
            "less than two groups")
        }
        val recSelectorKeys1: Seq[String] = JsonSelectors.findRecursivePathKeys(subgroups.head)
        val recSelectorKeys2: Seq[String] = JsonSelectors.findRecursivePathKeys(subgroups(1))
        Some(recursiveAndRecursiveSelectorKeysToSelector(recSelectorKeys1, recSelectorKeys2))
      case _ =>
        None
    }
  }


  def findPlainPathKeys(selector: String): Seq[String] = {
    plainPathKeyGroupingRegex.findAllIn(selector).subgroups
      .flatMap(x => x.split("""\\""").map(x => x.trim).filter(x => x.nonEmpty) match {
        case e if e.isEmpty => Seq.empty
        case e if e.length >= 1 => e
      })
  }

  def findRecursivePathKeys(selector: String): Seq[String] = {
    val subgroups: Seq[String] = recursivePathKeyGroupingRegex.findAllIn(selector).subgroups
    subgroups.flatMap(x => x.split("""\\""").map(x => x.trim).filter(x => x.nonEmpty) match {
      case e if e.isEmpty => Seq.empty
      case e if e.length >= 1 => e
    })
  }

  trait Selector[+T] {
    def select(jsValue: JsValue): T

    def select(jsValue: JsLookupResult): T
  }

  trait PlainSelector extends Selector[JsLookupResult] {
    def select(jsValue: JsValue): JsLookupResult

    def select(jsValue: JsLookupResult): JsLookupResult
  }

  trait JsValueSeqSelector extends Selector[collection.Seq[JsValue]] {
    def select(jsValue: JsValue): collection.Seq[JsValue]

    def select(lookupResult: JsLookupResult): collection.Seq[JsValue]
  }

  /**
   * Single key selector. Keeps JsLookupResult as outcome that can either
   * be JsDefined or JsUndefined, as some kind of Option equivalent
   * for selections
   *
   * @param key
   */
  case class SingleKeySelector(key: String) extends PlainSelector {
    override def select(jsValue: JsValue): JsLookupResult = jsValue \ key

    override def select(jsValue: JsLookupResult): JsLookupResult = jsValue \ key
  }

  /**
   * Utilizes recursive selector, e.g in case values for a single key
   * shall be selected from an array of json values.
   *
   * @param key - key to collect values for
   */
  case class RecursiveSelector(key: String) extends JsValueSeqSelector {
    override def select(jsValue: JsValue): collection.Seq[JsValue] = jsValue \\ key

    override def select(lookupResult: JsLookupResult): collection.Seq[JsValue] = lookupResult \\ key
  }

  /**
   * Passing a sequence of selectors, pick up the value that corresponds
   * to this selector path
   *
   * @param selectorKeys - sequential keys
   */
  case class PlainPathSelector(selectorKeys: Seq[String]) extends PlainSelector {

    val selectors: Seq[SingleKeySelector] = selectorKeys.map(x => SingleKeySelector(x))

    def select(lookup: JsLookupResult): JsLookupResult = lookup match {
      case JsDefined(value) => select(value)
      case undef: JsUndefined => undef
    }

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
  case class PlainAndRecursiveSelector(recursiveSelectorKey: String, plainSelectorKeys: String*) extends JsValueSeqSelector {
    def select(lookup: JsLookupResult): collection.Seq[JsValue] = lookup match {
      case JsDefined(value) => select(value)
      case _: JsUndefined => Seq.empty
    }

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

  case class ValueSelector[U, V](selector: Selector[U], mapFunc: SerializableFunction1[U, V]) {
    def select(jsonValue: JsValue): V = {
      val selected: U = selector.select(jsonValue)
      mapFunc.apply(selected)
    }
  }

  case class SingleValueSelector[T](plainSelectorKeys: Seq[String])(implicit reads: Reads[T]) {
    val selector: PlainPathSelector = PlainPathSelector(plainSelectorKeys)

    def select(jsValue: JsValue): T = {
      val value: JsLookupResult = selector.select(jsValue)
      value.as[T]
    }
  }

  /**
   * Selector config, allowing combination of plain path selectors and
   * recursive selector. Defines the result type as well to be used in
   * the result conversion.
   *
   * @param plainSelectorKeys    - seq of plain selector keys, can be empty
   * @param recursiveSelectorKey - key for recursive selector
   */
  case class RecursiveValueSelector[T](plainSelectorKeys: Seq[String],
                                       recursiveSelectorKey: String)(implicit reads: Reads[T]) {

    val selector: PlainAndRecursiveSelector = PlainAndRecursiveSelector(recursiveSelectorKey, plainSelectorKeys: _*)

    def select(jsValue: JsValue): Seq[T] = {
      val value: collection.Seq[JsValue] = selector.select(jsValue)
      value.map(x => x.as[T]).toSeq
    }
  }

  /**
   * From selection string in the form "\ key1 \ key2 \ key3..." create the Selector
   *
   * @param path
   * @return
   */
  def pathToPlainSelector(path: String): PlainSelector = {
    // path must start with "\" and end with some non-empty string that is not only "\" or "\\"
    val normedPath: String = path.trim
    if (!normedPath.startsWith("\\")) {
      throw new RuntimeException(s"plain path selector needs to start with '\', but is: $path")
    }
    val plainPathSelectorKeys: Seq[String] = findPlainPathKeys(path)
    PlainPathSelector(plainPathSelectorKeys)
  }

  def pathToSingleRecursiveSelector(path: String): JsValueSeqSelector = {
    // path must start with "\" and end with some non-empty string that is not only "\" or "\\"
    val normedPath: String = path.trim
    if (!normedPath.startsWith("\\") && !normedPath.startsWith("\\\\")) {
      throw new RuntimeException(s"recursive path selector needs to start with '\' or '\\', but is: $path")
    }
    if (normedPath.split("""\\\\""").length != 2) {
      throw new RuntimeException(s"recursive path selector needs to contain recursive selector '\\\\' once, but is: $path")
    }
    val recursivePathSelectorKeys = findRecursivePathKeys(path)
    recursivePathSelectorKeys.length match {
      case 0 => throw new RuntimeException(s"no selector identified in path: $path")
      case 1 => RecursiveSelector(recursivePathSelectorKeys.head)
      case e if e > 1 => PlainAndRecursiveSelector(recursivePathSelectorKeys.last,
        recursivePathSelectorKeys.slice(0, recursivePathSelectorKeys.size - 1): _*)
    }

  }

  /**
   * Generate the right selector. Can be either plain path or path containing a
   * recursive selector at its end
   *
   * @param path
   * @return
   */
  def classifyPath(path: String): Selector[_] = {
    if (recursivePathKeyGroupingRegex.matches(path)) {
      pathToSingleRecursiveSelector(path)
    }
    else pathToPlainSelector(path)
  }

  case class RecursiveAndPlainSelector(recursiveSelector: JsValueSeqSelector, plainSelector: PlainSelector) extends JsValueSeqSelector {
    override def select(jsValue: JsValue): collection.Seq[JsValue] = {
      recursiveSelector
        .select(jsValue)
        .map(x => plainSelector.select(x))
        .filter(x => x.isDefined)
        .map(x => x.get).toSeq
    }

    override def select(lookupResult: JsLookupResult): collection.Seq[JsValue] = {
      recursiveSelector
        .select(lookupResult)
        .map(x => plainSelector.select(x))
        .filter(x => x.isDefined)
        .map(x => x.get)
        .toSeq
    }
  }

  case class RecursiveAndRecursiveSelector(recursiveSelector: JsValueSeqSelector, otherRecursiveSelector: JsValueSeqSelector) extends JsValueSeqSelector {
    override def select(jsValue: JsValue): collection.Seq[JsValue] = {
      recursiveSelector.select(jsValue).flatMap(x => otherRecursiveSelector.select(x)).toSeq
    }

    override def select(lookupResult: JsLookupResult): collection.Seq[JsValue] = {
      recursiveSelector.select(lookupResult).flatMap(x => otherRecursiveSelector.select(x)).toSeq
    }
  }


}
