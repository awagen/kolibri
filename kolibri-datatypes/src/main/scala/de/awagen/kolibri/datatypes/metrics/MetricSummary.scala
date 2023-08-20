/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.datatypes.metrics

import de.awagen.kolibri.datatypes.metrics.MetricSummary.BestAndWorstConfigs
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.utils.{FuncUtils, MathUtils}
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import spray.json.DefaultJsonProtocol.{DoubleJsonFormat, StringJsonFormat, immSeqFormat, jsonFormat2, lazyFormat, mapFormat, rootFormat, tuple2Format}
import spray.json.RootJsonFormat


object MetricSummary {

  case class BestAndWorstConfigs(best: (Map[String, Seq[String]], Double), worst: (Map[String, Seq[String]], Double))


  /**
   * Summarize a sequence of tuples where first element is the Tag and the second
   * represents a collection of MetricRows that represent all rows for the given tag.
   * Per Tag summary stats are calculated, indicating:
   * - how much the distinct parameters change the results
   * - what good and bad configurations are, picking a representative parameter setting for good / bad configs in case there are multiple similar
   */
  def summarize(results: Seq[(Tag, Seq[MetricRow])], criterionMetricName: String): Map[Tag, MetricSummary] = {
    results.map(result => {
      val bestAndWorst = calculateBestAndWorstConfigs(result._2, criterionMetricName)
      val parameterEffectEstimate = calculateParameterEffectEstimate(result._2, criterionMetricName)
      (result._1, MetricSummary(bestAndWorst, parameterEffectEstimate))
    }).toMap
  }

  /**
   * Calculate for a single parameter and metric for every fixed value of the parameter descriptive stats
   * for the values of the metric. Changes in stats by varying the parameter values can show that the parameter
   * actually has an effect on the metric.
   *
   * @param metrics    - the mapping of the parameter settings to the actual metric results
   * @param metricName - the metric name to use for interpreting the effect of a single parameter
   * @return - mapping of parameter name to the variance value
   *
   *         for usage of apache commons math lib: https://commons.apache.org/proper/commons-math/userguide/stat.html
   */
  private[metrics] def calculateMetricStatsPerFixedParameter(metrics: Seq[MetricRow],
                                                             metricName: String,
                                                             paramName: String): Map[Seq[String], DescriptiveStatistics] = {
    val singleParamValueToMetricValuesMap: Map[Seq[String], Seq[Double]] = metrics.map(row => {
      val parameters = row.params
      val valueOpt: Option[Double] = row.metrics.get(metricName).map(value => value.biValue.value2.value.asInstanceOf[Double])
      (parameters.get(paramName), valueOpt)
    })
      .filter(x => x._2.nonEmpty && x._1.nonEmpty)
      .map(x => (x._1.get, x._2.get))
      .groupMap(x => x._1)(x => x._2)
    // for all values for the given parameter, calculate the descriptive stats
    singleParamValueToMetricValuesMap.map(x => {
      val stats = new DescriptiveStatistics()
      x._2.foreach(value => stats.addValue(value))
      (x._1, stats)
    })
  }

  /**
   * Given a sequence of descriptive stats for the respective metric values and a function for extracting double value,
   * return the max difference.
   *
   * @param stats     - sequence of descriptive stats of the single metric of interest
   * @param valueFunc - function picking the comparison value from the DescriptiveStatistics objects
   */
  private[metrics] def maxStatDifference(stats: Seq[DescriptiveStatistics], valueFunc: DescriptiveStatistics => Double = x => x.getPercentile(50)): Double = {
    val medianValues: Seq[Double] = stats.map(x => valueFunc(x))
    medianValues.max - medianValues.min
  }

  /**
   * Given seq of metric rows, and a criterion metric name, calculate for all parameters a numeric value giving
   * an estimate how much leverage changes in the value can have.
   * Note: its a relatively weak heuristic, such as changes in max/min median for distributions for distinct values
   * of the respective parameter.
   *
   * @param metrics    - the metric rows to take into account. The descriptive stats are formed for each constant distinct value
   *                   of the respective parameter.
   * @param metricName - the name of the metric used as criterion to evaluate the shift of distribution for distinct values of the respective
   *                   parameter values (per single parameter, over all parameter changes)
   * @return
   */
  def calculateParameterEffectEstimate(metrics: Seq[MetricRow],
                                       metricName: String): Map[String, Double] = {
    val parameterNames = metrics.head.params.keySet.toSeq
    parameterNames.map(paramName => {
      val paramStats: Map[Seq[String], DescriptiveStatistics] = calculateMetricStatsPerFixedParameter(metrics, metricName, paramName)
      val statsDiff: Double = maxStatDifference(paramStats.values.toSeq, x => x.getPercentile(50))
      (paramName, statsDiff)
    }).toMap
  }


  /**
   * Given seq of tuples of parameter settings and metric value, group the parameter settings that cause similar values
   * differing only by some threshold. The metric value used is the highest of the group.
   * The allowed difference in metric value is specified by the threshold parameter
   *
   * @param paramToValueTuples - Seq of (parameter values, metric value) tuples to apply the grouping on
   * @param threshold          - amount metric values for different parameter settings are allowed to deviate from another
   *                           to be still counted into the same group
   * @return
   */
  private[metrics] def groupParamsWithSimilarMetrics(paramToValueTuples: Seq[(Map[String, Seq[String]], Double)],
                                                     threshold: Double): Seq[(Double, Seq[Map[String, Seq[String]]])] = {
    paramToValueTuples.sortBy(x => x._2).reverse.foldLeft(Seq.empty[(Double, Seq[Map[String, Seq[String]]])])((oldSeq, newTuple) => (oldSeq, newTuple) match {
      case (seq, tuple) if seq.isEmpty =>
        Seq((tuple._2, Seq(tuple._1)))
      case (seq, tuple) if math.abs(seq.last._1 - tuple._2) <= threshold =>
        val lastBucket = (seq.last._1, seq.last._2 :+ tuple._1)
        seq.slice(0, seq.size - 1) :+ lastBucket
      case (seq, tuple) =>
        seq :+ (tuple._2, Seq(tuple._1))
    })
  }

  /**
   * Given a grouping, score all parameter settings belonging to the same group and pick the one with the lowest tie breaker
   * score as group representation
   *
   * @param groups
   * @return
   */
  private[metrics] def condenseGroups(groups: Seq[(Double, Seq[Map[String, Seq[String]]])],
                                      numParamsWeight: Double = 1000.0,
                                      paramUnitSumWeight: Double = 1.0): Seq[(Double, Map[String, Seq[String]])] = {
    groups.map(tuple => {
      val value = tuple._1
      val paramScorings = tuple._2.map(x => parameterScoreTieBreaker(x, numParamsWeight, paramUnitSumWeight))
      val tieBreakerMinParamTuple = paramScorings.zip(tuple._2).minBy(x => x._1)
      (value, tieBreakerMinParamTuple._2)
    })
  }

  /**
   * Determine best and worst parameter configuration.
   * Here we allow some threshold such that similarly well performing configs can be grouped
   * together. In case there are multiple best / worst configs, we utilize the
   * parameterScoreTieBreaker below to decide which one to pick.
   *
   * @param metrics
   * @param metricName
   * @return
   */
  def calculateBestAndWorstConfigs(metrics: Seq[MetricRow],
                                   metricName: String,
                                   differenceThreshold: Double = 0.01,
                                   numParamsWeight: Double = 1000.0,
                                   paramUnitSumWeight: Double = 1.0): BestAndWorstConfigs = {
    val paramsToMetricValueTuplesSorted: Seq[(Map[String, Seq[String]], Double)] = metrics.map(x => (x.params, x.metrics.get(metricName)))
      .filter(x => x._2.nonEmpty)
      .map(x => (x._1, x._2.get.biValue.value2.value.asInstanceOf[Double]))
      // sortBy on double would give lower values first, thus reversed below
      .sortBy(x => x._2)
      .reverse
    // now wed need to see which parameter settings lead to close enough metric values
    // and pick one setting for best and worst
    val groups = groupParamsWithSimilarMetrics(paramsToMetricValueTuplesSorted, differenceThreshold)
    val condensed = condenseGroups(groups, numParamsWeight, paramUnitSumWeight)
    BestAndWorstConfigs(condensed.head.swap, condensed.last.swap)
  }

  /**
   * Given the parameters for a particular configuration, determine a parameter score (independent of how it affects
   * the metric values, which is a separate score.
   * Here we apply a simple heuristic only on the numeric parameter values - we punish for two things:
   * - higher metric values (if two configs produce similar metric values, we pick the lower values).
   * Relatively low punishment, e.g 1 / metricUnit
   * - more params with non-zero values (e.g if two configurations lead to similar metric values, wed like to pick
   * those where the fewest mechanics are active (such as field boosts or the like in the case of search).
   * Strong punishment, e.g 1000 / nonZeroParam
   *
   * @param parameters - the respective parameters
   * @return - numeric score where lower means better
   */
  private[metrics] def parameterScoreTieBreaker(parameters: Map[String, Seq[String]],
                                                numParamsWeight: Double = 1000.0,
                                                paramUnitSumWeight: Double = 1.0): Double = {
    // filter parameters that have single values and are parsable as double
    val numParams: Map[String, Double] = parameters
      .filter(x => {
        x._2.size == 1 && FuncUtils.isExecutableStringOp[Double](y => y.toDouble)(x._2.head)
      })
      .map(x => (x._1, x._2.head.toDouble))
    // now count how many differ from 0
    val paramsGreaterZero: Int = numParams.filter(x => !MathUtils.equalWithPrecision(x._2, 0.0, 0.00001)).keySet.size
    val paramUnitValues: Double = numParams.values.sum
    paramsGreaterZero * numParamsWeight + paramUnitValues * paramUnitSumWeight
  }

  implicit val bestAndWorstConfigsFormat: RootJsonFormat[BestAndWorstConfigs] = jsonFormat2(BestAndWorstConfigs)
  implicit val metricSummaryFormat: RootJsonFormat[MetricSummary] = jsonFormat2(MetricSummary.apply)

}

// TODO: we might wanna extend this with a split of all configurations into value-buckets where each bucket
// gets a representative configuration assigned (e.g best, worst and 3 intervals inbetween or the like)
case class MetricSummary(bestAndWorstConfigs: BestAndWorstConfigs, parameterEffectEstimate: Map[String, Double])
