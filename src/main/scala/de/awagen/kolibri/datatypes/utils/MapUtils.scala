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

package de.awagen.kolibri.datatypes.utils

import org.slf4j
import org.slf4j.LoggerFactory

/**
  * if two maps can not be combined for a specific key, instead of a merged value this will be set (and subsequent combines wont work and remain CombineError)
  */
case object CombineError

object ValueCombine {

  val logger: slf4j.Logger = LoggerFactory.getLogger(ValueCombine.getClass.toString)

  /**
    * concatenates two sequences
    */
  val CONCAT_SEQ: (Seq[_], Seq[_]) => Seq[_] = (x, y) => x ++ y

  /**
    * this one combines two values the following way:
    * - String: appends strings separated with comma
    * - Seq: first Seq extended by second Seq
    * - Set: values of first set and values of second set
    * - Float/Double: calculates the weighted average, given the number of elements used to generate the respective values
    * - Other: if
    */
  val AVG_NUMERIC_APPEND_STR_AND_SEQ: ((Any, Int), (Any, Int)) => Any = {
    case ((a: Set[_], _), (b: Set[_], _)) => a ++ b
    case ((a: Seq[_], _), (b: Seq[_], _)) => a ++ b
    case ((a: String, _), (b: String, _)) => s"$a,$b"
    case ((a: Float, a_count: Int), (b: Float, b_count: Int)) => (a * a_count + b * b_count) / (a_count + b_count)
    case ((a: Double, a_count: Int), (b: Double, b_count: Int)) => (a * a_count + b * b_count) / (a_count + b_count)
    case ((a, _), (b, _)) =>
      logger.error(s"can not aggregate types '${a.getClass}' and '${b.getClass}'")
      CombineError
  }

}

object MapUtils {

  /**
    * combination function to merge two maps. This one keeps track of the count of entities used to generate the current map value, e.g to allow calculation of averages
    *
    * @param map1AndCount : a map and the count of entities used to generate the values
    * @param map2AndCount : a map and the count of entities used to generate the values
    * @param combineFunc  : the function describing how to combine two values
    * @tparam T : type parameter for the map values. If a mix, might use Any
    * @return the merged map
    */
  def combineMapsWithAggregateCounts[T](map1AndCount: (Map[String, T], Int), map2AndCount: (Map[String, T], Int), combineFunc: ((T, Int), (T, Int)) => T): Map[String, T] = {
    val map1Keys = map1AndCount._1.keys.toSeq
    val map2Keys = map2AndCount._1.keys.toSeq
    val allKeys: Seq[String] = map1Keys ++ map2Keys
    allKeys.map {
      case e if map1Keys.contains(e) && map2Keys.contains(e) => e -> combineFunc((map1AndCount._1(e), map1AndCount._2), (map2AndCount._1(e), map2AndCount._2))
      case e if map1Keys.contains(e) && !map2Keys.contains(e) => e -> map1AndCount._1(e)
      case e => e -> map2AndCount._1(e)
    }.toMap
  }

  /**
    * combination function to merge two maps. This one does not keep track of the count of entities used to generate the current map value, e.g real averages can not be calculated
    * (since we dont know how many single values went into calculation of the current value per map)
    *
    * @param map1        : a map
    * @param map2        : a map
    * @param combineFunc : the function describing how to combine two values
    * @tparam T : type parameter for the map values. If a mix, might use Any
    * @return the merged map
    */
  def combineMaps[T](map1: Map[String, T], map2: Map[String, T], combineFunc: (T, T) => T): Map[String, T] = {
    val map1Keys = map1.keys.toSeq
    val map2Keys = map2.keys.toSeq
    val allKeys: Seq[String] = map1Keys ++ map2Keys
    allKeys.map {
      case e if map1Keys.contains(e) && map2Keys.contains(e) => e -> combineFunc(map1(e), map2(e))
      case e if map1Keys.contains(e) && !map2Keys.contains(e) => e -> map1(e)
      case e => e -> map2(e)
    }.toMap
  }

}
