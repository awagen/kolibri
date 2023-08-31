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

import de.awagen.kolibri.datatypes.metrics.MetricSummaries.ParameterEffectEstimations.{calculateMaxMedianShiftForEachParameter, calculateMaxSingleResultShift}
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.utils.{FuncUtils, MathUtils}
import de.awagen.kolibri.datatypes.values.MetricValue
import de.awagen.kolibri.datatypes.values.MetricValueFunctions.AggregationType
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import spray.json.{DefaultJsonProtocol, JsonFormat}

object MetricSummaries extends DefaultJsonProtocol {

  object ParameterEffectEstimations {

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
    def calculateMaxMedianShiftForEachParameter(metrics: Seq[MetricRow],
                                                metricName: String): Map[String, Double] = {
      val parameterNames = metrics.head.params.keySet.toSeq
      parameterNames.map(paramName => {
        val paramStats: Map[Seq[String], DescriptiveStatistics] = calculateMetricStatsPerFixedParameter(metrics, metricName, paramName)
        val statsDiff: Double = maxStatDifference(paramStats.values.toSeq, x => x.getPercentile(50))
        (paramName, statsDiff)
      }).toMap
    }

    private[metrics] def metricRowToSettingIdentifierWithoutPassedParameter(metricRow: MetricRow, leaveOutParameterName: String): Seq[String] = {
      metricRow.params.removed(leaveOutParameterName).keySet.toSeq.sorted.flatMap(param => metricRow.params(param))
    }

    /**
     * Uses a bit more exhaustive approach to interpreting whether and how much of effect changing a single parameter
     * has. For this we group by all other parameters being the same, yielding a list of results where only
     * the parameter in question varies. The score will be the maximal difference, e.g max(seq) - min(seq)
     * (NOTE: need to filter out results where no successful sample was added, meaning the metric value will remain 0.0)
     * @param metrics - all rows of interest, reflecting all metric rows in the MetricDocument of interest (e.g the result assigned to a single tag / group; a single result file)
     * @param metricName - the name of the metric to be used as criterion for the evaluation
     * @return - The mapping of parameter name to the max difference in metric caused over all groups of results where the other parameters are kept constant and only one being varied.
     */
    def calculateMaxSingleResultShift(metrics: Seq[MetricRow],
                                      metricName: String): Map[String, Double] = {
      val parameterNames = metrics.head.params.keySet.toSeq
      parameterNames.map(paramName => {
        // if ignoring the passed parameter, give for all settings of the other parameters all metric results.
        // Thus the Seq[MetricRow] assigned for these settings only differ by different values for the parameter of interest.
        // We will max all of those parameter sequences to the max difference of metric values over their Seq[MetricRow].
        // To arrive at a final score for the parameter, we just take the max over all parameter settings.
        val valuesForParameterGroup: Map[Seq[String], Seq[MetricRow]] = metrics.groupMap(x => metricRowToSettingIdentifierWithoutPassedParameter(x, metricName))(identity[MetricRow])
        val maxDiffPerRow: Seq[Double] = valuesForParameterGroup.values.map(x => {
          val allValidValues = x.map(x => x.metrics.getOrElse(metricName, MetricValue.createDoubleAvgSuccessSample(metricName, -1.0, 1.0)))
            // only pick those values for which there is at least one non-fail sample
            .filter(x => x.biValue.value2.numSamples > 0)
            .map(x => x.biValue.value2.value.asInstanceOf[Double])
            .filter(v => v >= 0.0)
          val minVal = if (allValidValues.nonEmpty) allValidValues.min else 0.0
          val maxVal = if (allValidValues.nonEmpty) allValidValues.max else 0.0
          maxVal - minVal
        }).toSeq
        (paramName, maxDiffPerRow.max)
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
        val valueOpt: Option[Double] = row.metrics.get(metricName)
          // only use those values for which there is at least one (successful / non-fail) sample
          .filter(value => value.biValue.value2.numSamples > 0)
          .map(value => value.biValue.value2.value.asInstanceOf[Double])
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
      if (medianValues.nonEmpty) medianValues.max - medianValues.min else 0.0
    }

  }

  /**
   * Pick all metrics that are of type double and generate the summary for all metrics
   */
  def getMetricNameToSummaryMapping(results: Seq[(Tag, Seq[MetricRow])],
                                    differenceThreshold: Double = 0.01,
                                    numParamsWeight: Double = 1000.0,
                                    paramUnitSumWeight: Double = 1.0): Map[String, RunSummary] = {
    val allMetricNames = results.head._2.head.metricNames
    val allDoubleMetrics = allMetricNames
      .filter(x => results.head._2.head.metrics(x).biValue.value2.aggregationType == AggregationType.DOUBLE_AVG)
    allDoubleMetrics.map(metricName => (metricName, summarize(results, metricName, differenceThreshold, numParamsWeight, paramUnitSumWeight))).toMap
  }


  /**
   * Summarize a sequence of tuples where first element is the Tag and the second
   * represents a collection of MetricRows that represent all rows for the given tag.
   * Per Tag summary stats are calculated, indicating:
   * - how much the distinct parameters change the results
   * - what good and bad configurations are, picking a representative parameter setting for good / bad configs in case there are multiple similar
   */
  def summarize(results: Seq[(Tag, Seq[MetricRow])],
                criterionMetricName: String,
                differenceThreshold: Double = 0.01,
                numParamsWeight: Double = 1000.0,
                paramUnitSumWeight: Double = 1.0): RunSummary = {
    val tagToSummaryMap = results.map(result => {
      val bestAndWorst = calculateBestAndWorstConfigs(
        metrics = result._2,
        metricName = criterionMetricName,
        differenceThreshold = differenceThreshold,
        numParamsWeight = numParamsWeight,
        paramUnitSumWeight = paramUnitSumWeight
      )
      val medianShiftEffectEstimate = calculateMaxMedianShiftForEachParameter(result._2, criterionMetricName)
      val maxSingleResultShift = calculateMaxSingleResultShift(result._2, criterionMetricName)
      (result._1.toString, MetricSummary(bestAndWorst, Map("maxMedianShift" -> medianShiftEffectEstimate, "maxSingleResultShift" -> maxSingleResultShift)))
    }).toMap
    RunSummary(criterionMetricName, tagToSummaryMap)
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
      // value exists and has at least one non-fail sample
      .filter(x => x._2.nonEmpty && x._2.get.biValue.value2.numSamples > 0)
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

  case class BestAndWorstConfigs(best: (Map[String, Seq[String]], Double), worst: (Map[String, Seq[String]], Double))
  implicit val bestAndWorstConfigsFormat: JsonFormat[BestAndWorstConfigs] = jsonFormat2(BestAndWorstConfigs)

  // TODO: we might wanna extend this with a split of all configurations into value-buckets where each bucket
  // gets a representative configuration assigned (e.g best, worst and 3 intervals inbetween or the like)
  case class MetricSummary(bestAndWorstConfigs: BestAndWorstConfigs, parameterEffectEstimate: Map[String, Map[String, Double]])

  implicit val metricSummaryFormat: JsonFormat[MetricSummary] = jsonFormat2(MetricSummary.apply)

  case class RunSummary(metric: String, results: Map[String, MetricSummary])

  implicit val runSummaryFormat: JsonFormat[RunSummary] = jsonFormat2(RunSummary)

}

