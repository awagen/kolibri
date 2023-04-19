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


package de.awagen.kolibri.fleet.akka.config

import com.softwaremill.macwire.wire
import de.awagen.kolibri.base.processing.execution.functions.Execution
import de.awagen.kolibri.base.provider.WeightProviders.WeightProvider
import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors.{PlainAndRecursiveSelector, PlainPathSelector}
import de.awagen.kolibri.base.usecase.searchopt.parse.TypedJsonSelectors.{NamedAndTypedSelector, TypedJsonSeqSelector, TypedJsonSingleValueSelector}
import de.awagen.kolibri.base.usecase.searchopt.provider.FileBasedJudgementProvider.JudgementFileCSVFormatConfig
import de.awagen.kolibri.base.usecase.searchopt.provider.{FileBasedJudgementProvider, JudgementProvider}
import de.awagen.kolibri.datatypes.types.JsonTypeCast
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.fleet.akka.config.AppProperties.config._
import de.awagen.kolibri.fleet.akka.config.di.modules.connections.HttpModule
import de.awagen.kolibri.fleet.akka.config.di.modules.persistence.PersistenceModule
import de.awagen.kolibri.fleet.akka.io.json.ExecutionJsonProtocol.ExecutionFormat
import de.awagen.kolibri.fleet.akka.io.json.SearchEvaluationJsonProtocol
import de.awagen.kolibri.fleet.akka.io.json.WeightProviderJsonProtocol.StringWeightProviderFormat
import de.awagen.kolibri.fleet.akka.processing.JobMessages.SearchEvaluationDefinition
import spray.json.RootJsonFormat

object AppConfig {

  object JsonFormats {

    implicit val weightProviderFormat: RootJsonFormat[WeightProvider[String]] = StringWeightProviderFormat(
      persistenceModule.persistenceDIModule.reader
    )

    implicit val executionFormat: RootJsonFormat[Execution[Any]] = ExecutionFormat(
      persistenceModule.persistenceDIModule.reader,
      persistenceModule.persistenceDIModule.writer,
      AppProperties.config.metricDocumentFormatsMap,
      regex => persistenceModule.persistenceDIModule.dataOverviewReaderWithRegexFilter(regex),
      weightProviderFormat
    )

    implicit val searchEvaluationJsonFormat: RootJsonFormat[SearchEvaluationDefinition] =
      SearchEvaluationJsonProtocol.SearchEvaluationFormat(executionFormat)

  }

  val persistenceModule: PersistenceModule = wire[PersistenceModule]

  val httpModule: HttpModule = wire[HttpModule]

  val judgementFileFormatConfig: JudgementFileCSVFormatConfig = JudgementFileCSVFormatConfig(
    judgement_list_delimiter = judgementCSVFileColumnDelimiter,
    judgement_file_columns = judgementCSVFileIgnoreLessThanColumns,
    judgement_file_judgement_column = judgementCSVFileJudgementColumn,
    judgement_file_search_term_column = judgementCSVFileSearchTermColumn,
    judgement_file_product_id_column = judgementCSVFileProductIdColumn
  )

  val filepathToJudgementProvider: SerializableFunction1[String, JudgementProvider[Double]] = new SerializableFunction1[String, JudgementProvider[Double]] {

    override def apply(filepath: String): JudgementProvider[Double] = {
      judgementSourceType match {
        case "CSV" =>
          FileBasedJudgementProvider.createCSVBasedProvider(
            AppConfig.persistenceModule.persistenceDIModule.reader,
            filepath,
            judgementFileFormatConfig)
        case "JSON_LINES" =>
          val jsonQuerySelector: NamedAndTypedSelector[Option[Any]] = TypedJsonSingleValueSelector(
            name = "query",
            selector = PlainPathSelector(judgementJsonLinesPlainQueryPath),
            castType = JsonTypeCast.STRING
          )
          val jsonProductsSelector: TypedJsonSeqSelector = TypedJsonSeqSelector(name = "products", selector = PlainAndRecursiveSelector(recursiveSelectorKey = judgementJsonLinesPlainProductIdSelector, plainSelectorKeys = judgementJsonLinesPlainProductsPath: _*), castType = JsonTypeCast.STRING)
          val jsonJudgementsSelector: TypedJsonSeqSelector = TypedJsonSeqSelector(
            name = "judgements",
            selector = PlainAndRecursiveSelector(
              recursiveSelectorKey = judgementJsonLinesPlainJudgementSelector,
              plainSelectorKeys = judgementJsonLinesPlainProductsPath: _*
            ),
            castType = judgementJsonLinesJudgementValueTypeCast
          )
          val queryProductDelimiter: String = judgementQueryAndProductDelimiter
          FileBasedJudgementProvider.createJsonLineBasedProvider(
            AppConfig.persistenceModule.persistenceDIModule.reader,
            filepath,
            jsonQuerySelector,
            jsonProductsSelector,
            jsonJudgementsSelector,
            queryProductDelimiter)
      }
    }
  }

}
