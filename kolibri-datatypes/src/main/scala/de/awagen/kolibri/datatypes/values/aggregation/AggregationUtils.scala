package de.awagen.kolibri.datatypes.values.aggregation

import de.awagen.kolibri.datatypes.values.aggregation.immutable.AggregateValue

import scala.collection.mutable
import Numeric.Implicits._

object AggregationUtils {

  /**
   * Filtering all values passed with in instanceof[T] check. Note that this is not a deep type check due to
   * type erasure on compile, e.g if e: Map[String, Int], e.instanceof[Map[String, String]] will evaluate to true.
   * To do deep check, wed need to carry type tag on the checked values. Thus dont use this check if you have
   * very strict requirements and cant fail safely lateron.
   *
   * @param values - value to filter by instanceof[T]
   * @tparam T - (shallow) type to check
   * @return sequence of all elements of (shallow) type T
   */
  def filterValuesByInstanceOfCheck[T](values: AggregateValue[Any]*): Seq[AggregateValue[T]] = {
    values.filter(x => x.value.isInstanceOf[T]).map(x => x.asInstanceOf[AggregateValue[T]])
  }

  /**
   * Calculate new map from nested mappings, calculating sums of the values of the inner mappings
   * as values
   */
  def sumUpNestedNumericValueMaps[U, V](defaultValue: Double, weighted: Boolean, maps: AggregateValue[Map[U, Map[V, Double]]]*): Map[U, Map[V, Double]] = {
    val allKeys = maps.flatMap(x => x.value.keys).toSet
    allKeys.map(x => {
      (x, numericValueMapSumUp(defaultValue, weighted, maps.map(y => (y.value.getOrElse(x, Map.empty), y.weight)): _*))
    }).toMap
  }

  /**
   * Given tuples of Map and weight, calculate weighted sum per key, where each value is weighted
   * by the passed weight
   *
   * @param defaultValue      - in case the map doesnt contain a key, default value is used
   * @param valueWeightTuples - tuple of map and weight
   * @return - new map where values per key are weighted sums over all passed maps
   */
  def numericValueMapSumUp[T](defaultValue: Double, weighted: Boolean, valueWeightTuples: (Map[T, Double], Double)*): Map[T, Double] = {
    val allKeys = valueWeightTuples.flatMap(x => x._1.keys).toSet
    valueWeightTuples.foldLeft(mutable.Map.empty[T, Double])((mapSoFar, mapWeightTuple) => {
      val sampleWeight = if (weighted) mapWeightTuple._2 else 1.0
      allKeys.foreach(key => {
        val valueSoFar: Double = mapSoFar.getOrElse(key, defaultValue)
        val aggregateValueValue: Double = mapWeightTuple._1.getOrElse(key, defaultValue) * sampleWeight
        val updatedValue: Double = valueSoFar + aggregateValueValue
        mapSoFar(key) = updatedValue
      })
      mapSoFar
    }).toMap
  }

  /**
   * Per key, sum up the values of all passed maps with a weighted sum
   *
   * @param maps - the maps to aggregate
   * @tparam T - type of the key values
   * @return - aggregated map
   */
  def numericValueMapAggregateValueSumUp[T](defaultValue: Double, weighted: Boolean, maps: AggregateValue[Map[T, Double]]*): Map[T, Double] = {
    numericValueMapSumUp(defaultValue, weighted, maps.map(x => (x.value, x.weight)): _*)
  }

  /**
   * Create new mapping vom multiple AggregateValue maps taking weighting into account, e.g sum over all keys weighted
   * by weight result divided by the sum of the weights
   *
   * @param defaultValue - default value to fill in in case a map doesnt have a value for a specific key
   * @param maps
   * @return
   */
  def avgNumericValueMaps[T](defaultValue: Double, weighted: Boolean, maps: AggregateValue[Map[T, Double]]*): Map[T, Double] = {
    val totalWeight: Double = maps.map(x => x.weight).sum
    val addedValueMap: Map[T, Double] = numericValueMapAggregateValueSumUp(defaultValue, weighted, maps: _*)
    divideNumericMapValues(totalWeight, addedValueMap)
  }

  /**
   * Create new map where each value of the input map is divided by divideBy
   *
   * @param divideBy - value to divide by
   * @param map      - initial map
   * @return - new map where each value is the value of the initial map divided by divideBy
   */
  def divideNumericMapValues[T, U](divideBy: U, map: Map[T, U])(implicit ev: Fractional[U]): Map[T, U] = {
    map.map(kv => (kv._1, ev.div(kv._2, divideBy)))
  }

  def multiplyNumericMapValues[T, U](multiplyBy: U, map: Map[T, U])(implicit ev: Numeric[U]): Map[T, U] = {
    map.map(kv => (kv._1, kv._2 * multiplyBy))
  }


}
