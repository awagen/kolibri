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

package de.awagen.kolibri.base.usecase.searchopt.metrics

import de.awagen.kolibri.base.usecase.searchopt.metrics.Calculations.CalculationResult
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1

import scala.collection.immutable


/**
 * Given a sequence of "quality measures" in the range of [0,1],
 * defines some information retrieval metrics on a subset of
 * results
 */
object IRMetricFunctions {

  val NO_JUDGEMENTS: ComputeFailReason = ComputeFailReason("NO_JUDGEMENTS")
  val ZERO_DENOMINATOR: ComputeFailReason = ComputeFailReason("ZERO_DENOMINATOR")

  def dcgAtK(k: Int): SerializableFunction1[Seq[Double], CalculationResult[Double]] = new SerializableFunction1[Seq[Double], CalculationResult[Double]] {
    override def apply(seq: Seq[Double]): CalculationResult[Double] = {
      seq match {
        case _ if seq.isEmpty => Left(Seq(NO_JUDGEMENTS))
        case _ if seq.size == 1 => Right(seq.head)
        case _ =>
          val elements: Seq[Double] = seq.slice(1, k)
          var sum = seq.head
          elements.indices.foreach(x => sum += elements(x) / Math.log(x + 3))
          Right(sum)
      }
    }
  }

  def ndcgAtK(k: Int): SerializableFunction1[Seq[Double], CalculationResult[Double]] = new SerializableFunction1[Seq[Double], CalculationResult[Double]] {
    override def apply(seq: Seq[Double]): CalculationResult[Double] = {
      seq match {
        case e if e.isEmpty => Left(Seq(NO_JUDGEMENTS))
        case e if e.size == 1 =>
          if (e.head > 0.0) Right(1) else Left(Seq(ZERO_DENOMINATOR))
        case _ =>
          val idealScore = dcgAtK(k).apply(seq.sortWith(_ > _))
          val currentScore = dcgAtK(k).apply(seq)
          (currentScore, idealScore) match {
            case (_, Right(s2)) if s2 == 0 => Left(Seq(ZERO_DENOMINATOR))
            case (Right(s1), Right(s2)) if s2 > 0 => Right(s1 / s2)
            case (Left(r1), Left(r2)) => Left(r1 ++ r2)
            case (Left(r1), _) => Left(r1)
            case (_, Left(r2)) => Left(r2)
          }
      }
    }
  }

  def precisionAtK(n: Int, threshold: Double): SerializableFunction1[Seq[Double], CalculationResult[Double]] = new SerializableFunction1[Seq[Double], CalculationResult[Double]] {
    override def apply(seq: Seq[Double]): CalculationResult[Double] = {
      val allN = seq.slice(0, n)
      if (allN.nonEmpty) Right(allN.count(x => x >= threshold).toDouble / allN.size) else Left(Seq(NO_JUDGEMENTS))
    }
  }

  def recallAtK(n: Int, threshold: Double): SerializableFunction1[Seq[Double], CalculationResult[Double]] = new SerializableFunction1[Seq[Double], CalculationResult[Double]] {
    override def apply(seq: Seq[Double]): CalculationResult[Double] = {
      val maxNumOverThreshold = math.min(seq.count(x => x >= threshold), n)
      val allN = seq.slice(0, n)
      val overThresholdInFirstN = allN.count(x => x >= threshold)
      if (allN.nonEmpty) Right(overThresholdInFirstN.doubleValue / maxNumOverThreshold) else Left(Seq(NO_JUDGEMENTS))
    }
  }

  def errAtK(n: Int, maxGrade: Double): SerializableFunction1[Seq[Double], CalculationResult[Double]] = new SerializableFunction1[Seq[Double], CalculationResult[Double]] {
    override def apply(seq: Seq[Double]): CalculationResult[Double] = {
      seq match {
        case e if e.isEmpty => Left(Seq(NO_JUDGEMENTS))
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
