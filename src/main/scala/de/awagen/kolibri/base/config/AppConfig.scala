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


package de.awagen.kolibri.base.config

import com.softwaremill.macwire.wire
import de.awagen.kolibri.base.config.AppProperties.config._
import de.awagen.kolibri.base.config.di.modules.connections.HttpModule
import de.awagen.kolibri.base.config.di.modules.persistence.PersistenceModule
import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors.{PlainAndRecursiveSelector, PlainPathSelector}
import de.awagen.kolibri.base.usecase.searchopt.parse.TypedJsonSelectors.{SingleValueSelector, TypedJsonSeqSelector, TypedJsonSingleValueSelector}
import de.awagen.kolibri.base.usecase.searchopt.provider.FileBasedJudgementProvider.JudgementFileCSVFormatConfig
import de.awagen.kolibri.base.usecase.searchopt.provider.{FileBasedJudgementProvider, JudgementProvider}
import de.awagen.kolibri.datatypes.JsonTypeCast

object AppConfig {

  val persistenceModule: PersistenceModule = wire[PersistenceModule]

  val httpModule: HttpModule = wire[HttpModule]

  val judgementFileFormatConfig: JudgementFileCSVFormatConfig = JudgementFileCSVFormatConfig(
    judgement_list_delimiter = judgementCSVFileColumnDelimiter,
    judgement_file_columns = judgementCSVFileIgnoreLessThanColumns,
    judgement_file_judgement_column = judgementCSVFileJudgementColumn,
    judgement_file_search_term_column = judgementCSVFileSearchTermColumn,
    judgement_file_product_id_column = judgementCSVFileProductIdColumn
  )

  val filepathToJudgementProvider: String => JudgementProvider[Double] = {
    filepath => {
      judgementSourceType match {
        case "CSV" =>
          FileBasedJudgementProvider.createCSVBasedProvider(
            filepath,
            judgementFileFormatConfig)
        case "JSON_LINES" =>
          val jsonQuerySelector: SingleValueSelector[Any] = TypedJsonSingleValueSelector(name = "query", selector = PlainPathSelector(judgementJsonLinesPlainQueryPath), castType = JsonTypeCast.STRING)
          val jsonProductsSelector: TypedJsonSeqSelector = TypedJsonSeqSelector(name = "products", selector = PlainAndRecursiveSelector(recursiveSelectorKey = judgementJsonLinesPlainProductIdSelector, plainSelectorKeys = judgementJsonLinesPlainProductsPath:_*), castType = JsonTypeCast.STRING)
          val jsonJudgementsSelector: TypedJsonSeqSelector = TypedJsonSeqSelector(name = "judgements", selector = PlainAndRecursiveSelector(recursiveSelectorKey = judgementJsonLinesPlainJudgementSelector, plainSelectorKeys = judgementJsonLinesPlainProductsPath:_*), castType = JsonTypeCast.DOUBLE)
          val queryProductDelimiter: String = judgementQueryAndProductDelimiter
          FileBasedJudgementProvider.createJsonLineBasedProvider(
            filepath,
            jsonQuerySelector,
            jsonProductsSelector,
            jsonJudgementsSelector,
            queryProductDelimiter)
      }
    }
  }

}
