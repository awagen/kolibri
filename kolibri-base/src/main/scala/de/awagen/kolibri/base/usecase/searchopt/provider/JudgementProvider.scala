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

package de.awagen.kolibri.base.usecase.searchopt.provider

import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.values.Calculations.ComputeResult

trait JudgementProvider[T] extends Serializable with KolibriSerializable {

  def allJudgements: Map[String, T]

  def retrieveJudgementsForTerm(searchTerm: String): Map[String, T]

  /**
   * Provide sorted (descending) list of judgements of length k (if less than k judgements available,
   * return the available judgements)
   */
  def retrieveSortedJudgementsForTerm(searchTerm: String, k: Int): Seq[T]

  /**
   * To avoid repeated computation for ideal dcg values for a given k (number of results taken into account),
   * precompute for a selection of k here per query.
   */
  def getIdealDCGForTerm(searchTerm: String, k: Int): ComputeResult[Double]

  def retrieveJudgement(searchTerm: String, productId: String): Option[T]

  def retrieveJudgements(searchTerm: String, productIds: Seq[String]): Seq[Option[T]] = {
    productIds.map(x => retrieveJudgement(searchTerm, x))
  }

  def retrieveTermToJudgementMap(searchTerm: String, productIds: Seq[String]): Map[String, T] = {
    productIds.map(x => x -> retrieveJudgement(searchTerm, x)).filter(x => x._2.nonEmpty).map(x => x._1 -> x._2.get).toMap
  }

}
