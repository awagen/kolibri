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

//package de.awagen.kolibri.base.usecase.searchopt.domain
//
//import de.awagen.kolibri.base.domain.Connection
//import de.awagen.kolibri.base.domain.jobdefinitions.provider.QueryAndParamProvider
//import de.awagen.kolibri.base.http.client.request.{RequestContextProvider, RequestContextProviderBuilder}
//import de.awagen.kolibri.base.processing.execution.job.AttributeTraits.{Querying, Requesting}
//import de.awagen.kolibri.base.usecase.searchopt.metrics.MetricsEvaluation
//import de.awagen.kolibri.datatypes.values.DistinctValues
//import org.slf4j.{Logger, LoggerFactory}
//
//
//object SearchEvaluationDefinition {
//
//  val logger: Logger = LoggerFactory.getLogger("SearchEvaluationDefinition")
//
//
//  def createSearchExperiment(id: String,
//                             connections: Seq[Connection],
//                             queryAndParamProvider: QueryAndParamProvider,
//                             metricsEvaluation: MetricsEvaluation,
//                             queryParameter: String): SearchEvaluationDefinition = {
//    logger.info(s"Retrieving query terms from: ${queryAndParamProvider.queryFile}")
//
//    val terms = queryAndParamProvider.queryFileReader.read(queryAndParamProvider.queryFile)
//
//    logger.info(s"Finished reading of query terms, found ${terms.size} entries")
//
//    val contextProvider: RequestContextProvider =
//      new RequestContextProviderBuilder(id)
//        .withContextPath(queryAndParamProvider.contextPath)
//        .withDefaultParams(queryAndParamProvider.defaultParameters)
//        .withParameters(queryAndParamProvider.parameters.addValue(DistinctValues[String](queryParameter, terms), prepend = false))
//        .build
//
//    new SearchEvaluationDefinition(id, connections, contextProvider, metricsEvaluation, queryParameter)
//  }
//
//}
//
//
///**
//  * Provides information needed to execute requests against defined connections and execute evaluation.
//  * Defines requests to execute along with parsing function and definition of judgement-based metric calculations.
//  *
//  * @param id
//  * @param connections
//  * @param contextProvider
//  * @param metricsEvaluation
//  */
//// TODO: change the generic JobDefinition, where factory would generate the thing to execute,
//// given the below inputs (e.g would create source from contextProvider,
//// connection pool from given connections, and so on
//case class SearchEvaluationDefinition(id: String, connections: Seq[Connection], contextProvider: RequestContextProvider, metricsEvaluation: MetricsEvaluation,
//                                      queryParameter: String) extends Querying with Requesting with MetricsDefining with Serializable {
//
//  def splitIntoAtMostNrOfBatches(nrOfBatches: Int): Seq[SearchEvaluationDefinition] = {
//    contextProvider.splitIntoAtMostNrOfBatches(nrOfBatches).to(LazyList)
//      .map(x => new SearchEvaluationDefinition(id, connections, x, metricsEvaluation, queryParameter))
//  }
//
//  def splitIntoBatchIteratorAtMostNrOfBatches(nrOfBatches: Int): Iterator[SearchEvaluationDefinition] = {
//    contextProvider.splitIntoBatchIteratorAtMostNrOfBatches(nrOfBatches)
//      .map(x => new SearchEvaluationDefinition(id, connections, x, metricsEvaluation, queryParameter))
//  }
//
//  def splitIntoBatchesOfSize(batchSize: Int): Seq[SearchEvaluationDefinition] = {
//    contextProvider.splitIntoBatchesOfSize(batchSize).to(LazyList)
//      .map(x => new SearchEvaluationDefinition(id, connections, x, metricsEvaluation, queryParameter))
//  }
//
//  def splitIntoBatchIteratorOfSize(batchSize: Int): Iterator[SearchEvaluationDefinition] = {
//    contextProvider.splitIntoBatchIteratorOfSize(batchSize)
//      .map(x => new SearchEvaluationDefinition(id, connections, x, metricsEvaluation, queryParameter))
//  }
//
//  def splitIntoBatchesPerParamValue(paramName: String): Iterator[SearchEvaluationDefinition] = {
//    contextProvider.splitIntoBatchesPerParamValue(paramName)
//      .map(x => new SearchEvaluationDefinition(id, connections, x, metricsEvaluation, queryParameter))
//  }
//
//
//}
