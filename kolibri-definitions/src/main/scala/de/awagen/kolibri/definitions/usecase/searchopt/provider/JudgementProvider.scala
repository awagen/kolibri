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

package de.awagen.kolibri.definitions.usecase.searchopt.provider

import de.awagen.kolibri.datatypes.io.KolibriSerializable


trait JudgementProvider[T] extends Serializable with KolibriSerializable {

  /**
   * @param searchTerm - term for which to retrieve all judgements.
   * @return Map of all key - judgementValue mappings for the passed query. Note that the key is a composition of the
   *         term and entity identifier, which is a detail of the specific implementation.
   */
  def retrieveJudgementsForTerm(searchTerm: String): Map[String, T]

  /**
   * Provide sorted (descending) list of judgements of length k (if less than k judgements available,
   * return the available judgements)
   */
  def retrieveSortedJudgementsForTerm(searchTerm: String, k: Int): Seq[T]

  /**
   * Retrieve single judgement value
   * @param searchTerm - query for which judgement shall be retrieved
   * @param productId - product if for which judgement shall be retrieved
   * @return nonEmpty value if known, empty if unknown
   */
  def retrieveJudgement(searchTerm: String, productId: String): Option[T]

  /**
   * Retrieve judgements for a given term and all passed productIds
   */
  def retrieveJudgements(searchTerm: String, productIds: Seq[String]): Seq[Option[T]] = {
    productIds.map(x => retrieveJudgement(searchTerm, x))
  }

  /**
   * For a given term and list of product ids, return the key-value mapping for containing only the known values.
   * If any of the passed productIds has no known value for the given term, no entry will appear in the map for
   * this productId.
   */
  def retrieveTermToJudgementMap(searchTerm: String, productIds: Seq[String]): Map[String, T] = {
    productIds.map(x => x -> retrieveJudgement(searchTerm, x)).filter(x => x._2.nonEmpty).map(x => x._1 -> x._2.get).toMap
  }

}
