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

import de.awagen.kolibri.base.config.AppConfig.persistenceModule.persistenceDIModule
import de.awagen.kolibri.base.config.AppProperties.config.{idealDcgPreComputeStartK, idealDcgPreComputeStepSize, maxPrecomputeIdealDcgKValue, topKJudgementsPerQueryStorageSize}
import de.awagen.kolibri.base.io.reader.{FileReaderUtils, Reader}
import de.awagen.kolibri.base.usecase.searchopt.metrics.IRMetricFunctions
import de.awagen.kolibri.base.usecase.searchopt.parse.TypedJsonSelectors.NamedAndTypedSelector
import de.awagen.kolibri.datatypes.reason.ComputeFailReason
import de.awagen.kolibri.datatypes.values.Calculations.ComputeResult
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json

import scala.io.Source


object FileBasedJudgementProvider {

  case class JudgementFileCSVFormatConfig(judgement_list_delimiter: String,
                                          judgement_file_columns: Int,
                                          judgement_file_judgement_column: Int,
                                          judgement_file_search_term_column: Int,
                                          judgement_file_product_id_column: Int)

  val defaultJudgementFileFormatConfig: JudgementFileCSVFormatConfig = JudgementFileCSVFormatConfig(
    judgement_list_delimiter = "\u0000",
    judgement_file_columns = 3,
    judgement_file_judgement_column = 2,
    judgement_file_search_term_column = 0,
    judgement_file_product_id_column = 1)

  /**
    * file based judgement provider assuming the file format is CSV
    *
    * @param filepath                  - file path
    * @param judgementFileFormatConfig - the column config indicating from which columns to extract the data
    * @param queryProductDelimiter     - separator of query and productId to use when creating the key to store the judgement under
    * @return
    */
  def createCSVBasedProvider(filepath: String,
                             judgementFileFormatConfig: JudgementFileCSVFormatConfig = defaultJudgementFileFormatConfig,
                             queryProductDelimiter: String = "\u0000"): FileBasedJudgementProvider = {
    new FileBasedJudgementProvider(filepath,
      persistenceDIModule.reader,
      csvSourceToJudgementMapping(
        judgementFileFormatConfig = judgementFileFormatConfig,
        queryProductDelimiter = queryProductDelimiter
      ),
      queryProductDelimiter = queryProductDelimiter
    )
  }

  /**
    * Creates the file based judgement provider assuming the file contains per line a json that represents the data state for a single query
    *
    * @param filepath               - file path
    * @param jsonQuerySelector      - selector to extract the query from a single json (in this case a single line in the file)
    * @param jsonProductsSelector   - selector to retrieve all products in order of appearance
    * @param jsonJudgementsSelector - selector to retrieve the judgements in order of appearance
    * @param queryProductDelimiter  - separator of query and productId to use when creating the key to store the judgement under
    * @return
    */
  def createJsonLineBasedProvider(filepath: String,
                                  jsonQuerySelector: NamedAndTypedSelector[Option[Any]],
                                  jsonProductsSelector: NamedAndTypedSelector[Seq[Any]],
                                  jsonJudgementsSelector: NamedAndTypedSelector[Seq[Any]],
                                  queryProductDelimiter: String = "\u0000"): FileBasedJudgementProvider = {
    new FileBasedJudgementProvider(
      filepath,
      persistenceDIModule.reader,
      jsonLineSourceToJudgementMapping(
        jsonQuerySelector,
        jsonProductsSelector,
        jsonJudgementsSelector,
        queryProductDelimiter),
      queryProductDelimiter
    )
  }

  def convertStringOrDoubleAnyToDouble(any: Any): Double = {
    any match {
      case str: String => str.toDouble
      case _ => any.asInstanceOf[Double]
    }
  }

  /**
    * This assumes that the judgement file contains one json per line.
    * An example could be {"query", "products": [{"product_id": "abc", "score": 0.231}, ...]}, and the passed selectors
    * need to take the specifics of the format into account
    *
    * @param jsonQuerySelector      - selector to extract the query per json
    * @param jsonProductsSelector   - selector to extract the product_ids per json
    * @param jsonJudgementsSelector - selector to extract the judgements per json
    * @param queryProductDelimiter  - delimiter used to combine query and product to a single key
    * @return
    */
  def jsonLineSourceToJudgementMapping(jsonQuerySelector: NamedAndTypedSelector[Option[Any]],
                                       jsonProductsSelector: NamedAndTypedSelector[Seq[Any]],
                                       jsonJudgementsSelector: NamedAndTypedSelector[Seq[Any]],
                                       queryProductDelimiter: String = "\u0000"): Source => Map[String, Double] = {
    source => {
      source.getLines()
        .map(line => line.trim)
        .filter(line => line.nonEmpty)
        .map(line => Json.parse(line))
        .flatMap(jsValue => {
          val query: String = jsonQuerySelector.select(jsValue).getOrElse("").asInstanceOf[String]
          val products: Seq[String] = jsonProductsSelector.select(jsValue).asInstanceOf[Seq[String]]
          val judgements: Seq[Double] = jsonJudgementsSelector.select(jsValue).map(x => convertStringOrDoubleAnyToDouble(x))
          val keys: Seq[String] = products.map(product => s"$query$queryProductDelimiter$product")
          keys zip judgements
        })
        .toMap
    }
  }

  /**
    * Transform a source in csv format into the judgement mapping.
    * The columns are taken from the respective format config
    *
    * @param judgementFileFormatConfig - config of columns
    * @param queryProductDelimiter     - the delimiter used to create the query - product - keys
    * @return
    */
  def csvSourceToJudgementMapping(judgementFileFormatConfig: JudgementFileCSVFormatConfig = defaultJudgementFileFormatConfig,
                                  queryProductDelimiter: String = "\u0000"): Source => Map[String, Double] = {
    FileReaderUtils.mappingFromCSVSource[Double](
      judgementFileFormatConfig.judgement_list_delimiter,
      judgementFileFormatConfig.judgement_file_columns,
      x => s"${x(judgementFileFormatConfig.judgement_file_search_term_column)}$queryProductDelimiter${x(judgementFileFormatConfig.judgement_file_product_id_column)}",
      x => x(judgementFileFormatConfig.judgement_file_judgement_column).toDouble)
  }
}


class BaseJudgementProvider(judgementStorage: Map[String, Double],
                                              queryProductDelimiter: String = "\u0000") extends JudgementProvider[Double] {

  private val uniqueQueries: Set[String] = judgementStorage.keys.map(key => keyToSearchTermAndProductId(key)._1).toSet
  // storing sorted list of judgements of given default size to reuse in calculations without resort
  private val sortedJudgementsPerQueryStorage: Map[String, Seq[Double]] = uniqueQueries
    .map(query => (query, composeSortedJudgementsForTerm(query, topKJudgementsPerQueryStorageSize)))
    .toMap
  // storing prepared list of ideal dcg values per query in steps of two till max value
  private val idealDcgPerQueryAndKStorage: Map[String, ComputeResult[Double]] = uniqueQueries
    .flatMap(query => {
      Range(idealDcgPreComputeStartK, maxPrecomputeIdealDcgKValue , idealDcgPreComputeStepSize).inclusive
        .map(k => (createKey(query, k.toString), composeIdealDCGForTerm(query, k)))
    }).toMap

  override def retrieveJudgement(searchTerm: String, productId: String): Option[Double] = {
    judgementStorage.get(createKey(searchTerm, productId))
  }

  /**
   * Create a key made of two ids. E.g in case of judgement storage is used for id1= query, id2 = productId,
   * in the case of ideal dcg values combines id1=query with id2=k-value
   * @param id1 - first id
   * @param id2 - second id
   * @return combined identifier
   */
  private[provider] def createKey(id1: String, id2: String): String = {
    s"$id1$queryProductDelimiter$id2"
  }

  private[provider] def keyToSearchTermAndProductId(key: String): (String, String) = {
    val parts = key.split(queryProductDelimiter)
    (parts.head, parts(1))
  }

  override def allJudgements: Map[String, Double] = collection.immutable.Map[String, Double]() ++ judgementStorage

  override def retrieveJudgementsForTerm(searchTerm: String): Map[String, Double] = judgementStorage
    .map(x => (keyToSearchTermAndProductId(x._1), x._2))
    .filter(x => x._1._1 == searchTerm)
    .map(x => (x._1._2, x._2))

  /**
   * Calculate sorted (descending) list of judgements of length k (or if less judgements available of size equal
   * to number of judgements available)
   */
  private[provider] def composeSortedJudgementsForTerm(searchTerm: String, k: Int): Seq[Double] = judgementStorage.keys
    .filter(key => key.startsWith(s"$searchTerm$queryProductDelimiter"))
    .map(key => judgementStorage(key))
    .toSeq
    .sorted
    .reverse
    .take(k)

  /**
   * To avoid repeated computation for ideal dcg values for a given k (number of results taken into account),
   * precompute for a selection of k here per query.
   */
  private[provider] def composeIdealDCGForTerm(searchTerm: String, k: Int): ComputeResult[Double] = {
    IRMetricFunctions.dcgAtK(k)(retrieveSortedJudgementsForTerm(searchTerm, k))
  }

  /**
   * Provide sorted (descending) list of judgements of length k (or if less judgements available of size equal
   * to number of judgements available)
   */
  override def retrieveSortedJudgementsForTerm(searchTerm: String, k: Int): Seq[Double] =
    sortedJudgementsPerQueryStorage.get(searchTerm).map(list => list.take(k)).getOrElse(Seq.empty)

  /**
   * Provide precomputed outcome of calculation of ideal dcg values for distinct values of k.
   * Note that the k-values for which data is precomputed / available depends on the config
   * parameters above.
   * @param searchTerm - search term for which to provide the result
   * @param k - k value (number of results taken into account) when calculating the ideal dcg
   * @return - the result of the compute. Is either Double for the metrics or a Seq of ComputeFailReason instances
   */
  override def getIdealDCGForTerm(searchTerm: String, k: Int): ComputeResult[Double] = {
    val idealDCGKey = createKey(searchTerm, k.toString)
    idealDcgPerQueryAndKStorage
      .getOrElse(idealDCGKey,
        Left(Seq(ComputeFailReason(s"No ideal dcg available for term '$searchTerm' and k = $k"))))
  }

}

/**
  * File based judgement provider. Takes distinct mapping functions depending on the format
  *
  * @param filepath                     - path to the file
  * @param fileReader                   - reader to use
  * @param sourceToJudgementMappingFunc - mapping function of source to judgement mapping (assuming key = [query][queryProductDelimiter][productId]
  * @param queryProductDelimiter        - separator of query and productId for key generation
  */
private[provider] class FileBasedJudgementProvider(filepath: String,
                                                   fileReader: Reader[String, Seq[String]],
                                                   sourceToJudgementMappingFunc: Source => Map[String, Double],
                                                   queryProductDelimiter: String = "\u0000")
  extends BaseJudgementProvider(sourceToJudgementMappingFunc.apply(fileReader.getSource(filepath)), queryProductDelimiter)