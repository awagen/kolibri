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

package de.awagen.kolibri.definitions.usecase.searchopt.metrics

import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementInfo
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.datatypes.values.Calculations.ComputeResult

import scala.collection.immutable


/**
 * Given a sequence of "quality measures" in the range of [0,1],
 * defines some information retrieval metrics on a subset of
 * results
 */
object IRMetricFunctions {

  val NO_JUDGEMENTS: ComputeFailReason = ComputeFailReason("NO_JUDGEMENTS")
  val ZERO_DENOMINATOR: ComputeFailReason = ComputeFailReason("ZERO_DENOMINATOR")

  private[metrics] def dcgAtKFromJudgementSeq(k: Int): SerializableFunction1[Seq[Double], ComputeResult[Double]] = new SerializableFunction1[Seq[Double], ComputeResult[Double]] {
    override def apply(seq: Seq[Double]): ComputeResult[Double] = {
      seq match {
        case _ if seq.isEmpty => Left(Seq(ComputeFailReason.NO_RESULTS))
        case _ if seq.size == 1 => Right(seq.head)
        case _ =>
          val elements: Seq[Double] = seq.slice(1, k)
          var sum = seq.head
          elements.indices.foreach(x => sum += elements(x) / Math.log(x + 3))
          Right(sum)
      }
    }
  }

  def dcgAtK(k: Int): SerializableFunction1[JudgementInfo, ComputeResult[Double]] = new SerializableFunction1[JudgementInfo, ComputeResult[Double]] {
    override def apply(info: JudgementInfo): ComputeResult[Double] = {
      dcgAtKFromJudgementSeq(k)(info.currentJudgements)
    }
  }

  private[metrics] def getNDCGFromIdealAndCurrentDCGComputeResults(idealDCGResult: ComputeResult[Double],
                                                                   currentDCGResult: ComputeResult[Double]): ComputeResult[Double] = {
    (idealDCGResult, currentDCGResult) match {
      case (Right(ideal), Right(current)) =>
        if (ideal == 0.0) Left(Seq(ZERO_DENOMINATOR))
        else Right(current / ideal)
      case (a1, a2) =>
        val combinedFailReasons: Set[ComputeFailReason] = (a1.swap.getOrElse(Seq.empty) ++ a2.swap.getOrElse(Seq.empty)).toSet
        Left(combinedFailReasons.toSeq)
    }
  }

  def ndcgAtK(k: Int): SerializableFunction1[JudgementInfo, ComputeResult[Double]] = new SerializableFunction1[JudgementInfo, ComputeResult[Double]] {
    override def apply(info: JudgementInfo): ComputeResult[Double] = {
      val seq = info.currentJudgements
      seq match {
        case e if e.isEmpty => Left(Seq(ComputeFailReason.NO_RESULTS))
        case e if e.size == 1 =>
          if (e.head > 0.0) Right(1) else Left(Seq(ZERO_DENOMINATOR))
        case _ =>
          val currentDCG = dcgAtKFromJudgementSeq(k)(seq)
          val idealDCG = dcgAtKFromJudgementSeq(k)(info.idealJudgements)
          getNDCGFromIdealAndCurrentDCGComputeResults(idealDCG, currentDCG)
      }
    }
  }

  def precisionAtKFromJudgementSeq(seq: Seq[Double], n: Int, threshold: Double): ComputeResult[Double] = {
    val allN = seq.slice(0, n)
    if (allN.nonEmpty) Right(allN.count(x => x >= threshold).toDouble / allN.size) else Left(Seq(NO_JUDGEMENTS))
  }

  def precisionAtK(n: Int, threshold: Double): SerializableFunction1[JudgementInfo, ComputeResult[Double]] = new SerializableFunction1[JudgementInfo, ComputeResult[Double]] {
    override def apply(info: JudgementInfo): ComputeResult[Double] = {
      val seq = info.currentJudgements
      precisionAtKFromJudgementSeq(seq, n, threshold)
    }
  }

  def recallAtK(n: Int, threshold: Double): SerializableFunction1[JudgementInfo, ComputeResult[Double]] = new SerializableFunction1[JudgementInfo, ComputeResult[Double]] {
    override def apply(info: JudgementInfo): ComputeResult[Double] = {
      val currentOverThreshold: Int = info.getCurrentSortOverThresholdCountForK(threshold, n)
      val maxOverThreshold: Int = info.getIdealSortOverThresholdCountForK(threshold, n)
      if (maxOverThreshold == 0) Left(Seq(ZERO_DENOMINATOR)) else Right(currentOverThreshold.doubleValue / maxOverThreshold.doubleValue)
    }
  }

  def errAtK(n: Int, maxGrade: Double): SerializableFunction1[JudgementInfo, ComputeResult[Double]] = new SerializableFunction1[JudgementInfo, ComputeResult[Double]] {
    override def apply(info: JudgementInfo): ComputeResult[Double] = {
      val seq = info.currentJudgements
      seq match {
        case e if e.isEmpty => Left(Seq(ComputeFailReason.NO_RESULTS))
        case _ =>
          val norm: Double = Math.pow(2, maxGrade)
          val probabilityThatUserLikesHit: immutable.Seq[Double] = seq.slice(0, n).indices
            .map(x => (Math.pow(2, seq(x) * maxGrade) - 1) / norm)
          var err: Double = 0.0
          var remainingProb = 1.0
          probabilityThatUserLikesHit.indices.foreach(
            x => {
              val prob = probabilityThatUserLikesHit(x)
              err += remainingProb * prob / (x + 1.0)
              remainingProb = remainingProb * (1.0 - prob)
            }
          )
          Right(err)
      }
    }
  }
}
