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

package de.awagen.kolibri.base.usecase.searchopt.provider

import de.awagen.kolibri.base.usecase.searchopt.metrics.JudgementHandlingStrategy
import de.awagen.kolibri.base.usecase.searchopt.metrics.JudgementValidation.JudgementValidation


/**
 * Any judgement info instance holds for the combination of a query and a list of products.
 * This specificity concerns the extracted judgements, the prepared judgements (e.g after imputing missing values),
 * the validation of the given judgements and the specific values of ideal judgements sortings.
 * Note that in any case the size of the current values and ideal values are given by the size of the passed product
 * list.
 */
object JudgementInfo {

  private[provider] def validateAndReturnFailedJudgements(judgements: Seq[Option[Double]], judgementHandling: JudgementHandlingStrategy): Seq[JudgementValidation] = {
    judgementHandling.validateAndReturnFailed(judgements)
  }

  private[provider] def getOverThresholdCountForK(threshold: Double, k: Int, values: Seq[Double]): Int = {
    values.take(k).count(value => value >= threshold)
  }

  def create(query: String,
             products: Seq[String],
             judgementProvider: JudgementProvider[Double],
             judgementHandling: JudgementHandlingStrategy): JudgementInfo = {
    // retrieve judgement values for the passed products and queries. Empty values indicate non-existence of judgements
    val currentJudgements: Seq[Option[Double]] = judgementProvider.retrieveJudgements(query, products)
    // check whether any validation criteria are violated for the given set of judgements
    val failedValidations: Seq[JudgementValidation] = validateAndReturnFailedJudgements(currentJudgements, judgementHandling)
    // extract missing positions so that we can fill them into the ideal sorting lateron to make sure
    // any value imputation via JudgementHandlingStrategy is considered.
    val missingJudgementPositions = currentJudgements.indices.filter(index => currentJudgements(index).isEmpty)
    // ideal values are all given since they are picked from the existing judgement values
    // we will still
    val idealJudgements: Seq[Double] = judgementProvider.retrieveSortedJudgementsForTerm(query, products.size)
    // now apply imputation according to the passed JudgementHandlingStrategy
    val preparedCurrentJudgements = judgementHandling.extractValues(currentJudgements)
    // here we adjust the ideal sorting by the fact that the extraction of values above might set values != 0.0
    // for missing values, which could cause metrics expected to be normed by an ideal sorting to exceed the
    // expected value range (e.g NDCG > 1.0)
    val adjustedIdealJudgements = (idealJudgements ++ missingJudgementPositions.map(position => preparedCurrentJudgements(position)))
      .sorted
      .reverse
      .take(products.size)

    JudgementInfo(
      query,
      products,
      preparedCurrentJudgements,
      adjustedIdealJudgements,
      failedValidations
    )

  }

}


/**
 *
 * @param query - query for which the data was extracted
 * @param products - the products / results for which the data was extracted
 * @param currentJudgements - the judgements for the above products in order of appearance in the passed products.
 *                          At this point missing values are handled already, so the Seq will contain imputed values
 *                          for each missing judgement for a particular product.
 * @param idealJudgements - the ideal judgements as derived from all known judgements (as given by JudgementProvider).
 *                        Note that we assume at this point that the ideal judgements were corrected for any imputations
 *                        in the currentJudgements list. This means that values not known in the given judgements but
 *                        imputed to get the currentJudgements are considered when the ideal sorting is derived.
 *                        In case imputation of unknown values is always done with 0.0, the adjusted ideal sorting will
 *                        be the same as the original ideal sorting as purely given by known judgement values.
 * @param failedJudgementValidations - in case validations on available judgements fails, this Seq will be non-empty
 *                                   and the results are seen to be invalid (e.g in case of too many missing judgements)
 */
case class JudgementInfo(query: String,
                         products: Seq[String],
                         currentJudgements: Seq[Double],
                         idealJudgements: Seq[Double],
                         failedJudgementValidations: Seq[JudgementValidation]) {

  /**
   * @param threshold - threshold the values need to exceed to be counted in
   * @param k - number of values (from the start) to consider
   * @return The count of values exceeding the threshold
   */
  def getIdealSortOverThresholdCountForK(threshold: Double, k: Int): Int = {
    JudgementInfo.getOverThresholdCountForK(threshold, k, idealJudgements)
  }

  def getCurrentSortOverThresholdCountForK(threshold: Double, k: Int): Int = {
    JudgementInfo.getOverThresholdCountForK(threshold, k, currentJudgements)
  }
}
