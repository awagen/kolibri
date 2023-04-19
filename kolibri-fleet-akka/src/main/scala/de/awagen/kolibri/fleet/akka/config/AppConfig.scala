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
import de.awagen.kolibri.definitions.io.json
import de.awagen.kolibri.definitions.io.json.ExecutionJsonProtocol.ExecutionFormat
import de.awagen.kolibri.definitions.io.json.{IndexedGeneratorJsonProtocol, MappingSupplierJsonProtocol, ModifierGeneratorProviderJsonProtocol, ModifierMappersJsonProtocol, OrderedMultiValuesJsonProtocol, OrderedValuesJsonProtocol, ParameterValuesJsonProtocol, ResourceDirectiveJsonProtocol, SeqModifierGeneratorJsonProtocol, SupplierJsonProtocol}
import de.awagen.kolibri.definitions.io.json.WeightProviderJsonProtocol.StringWeightProviderFormat
import de.awagen.kolibri.definitions.processing.execution.functions.Execution
import de.awagen.kolibri.definitions.provider.WeightProviders.WeightProvider
import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors.{PlainAndRecursiveSelector, PlainPathSelector}
import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors.{NamedAndTypedSelector, TypedJsonSeqSelector, TypedJsonSingleValueSelector}
import de.awagen.kolibri.definitions.usecase.searchopt.provider.FileBasedJudgementProvider.JudgementFileCSVFormatConfig
import de.awagen.kolibri.definitions.usecase.searchopt.provider.{FileBasedJudgementProvider, JudgementProvider}
import de.awagen.kolibri.datatypes.types.JsonTypeCast
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.fleet.akka.cluster.ClusterNodeObj
import de.awagen.kolibri.fleet.akka.config.AppProperties.config._
import de.awagen.kolibri.fleet.akka.config.di.modules.connections.HttpModule
import de.awagen.kolibri.fleet.akka.config.di.modules.persistence.PersistenceModule
import de.awagen.kolibri.fleet.akka.io.json.QueryBasedSearchEvaluationJsonProtocol.QueryBasedSearchEvaluationFormat
import de.awagen.kolibri.fleet.akka.io.json._
import de.awagen.kolibri.fleet.akka.processing.JobMessages.SearchEvaluationDefinition
import de.awagen.kolibri.storage.io.reader.DataOverviewReader
import spray.json.RootJsonFormat

import scala.util.matching.Regex

object AppConfig {

  object JsonFormats {

    val dataOverviewReaderWithRegexFilterFunc: SerializableFunction1[Regex, DataOverviewReader] = new SerializableFunction1[Regex, DataOverviewReader] {
      override def apply(regex: Regex): DataOverviewReader = persistenceModule.persistenceDIModule.dataOverviewReaderWithRegexFilter(regex)
    }

    val dataOverviewReaderWithSuffixFilterFunc: SerializableFunction1[String, DataOverviewReader] = new SerializableFunction1[String, DataOverviewReader] {
      override def apply(suffix: String): DataOverviewReader = persistenceModule.persistenceDIModule.dataOverviewReader(x => x.endsWith(suffix))
    }

    val dataOverviewReaderWithConditionFunc: SerializableFunction1[String => Boolean, DataOverviewReader] = new SerializableFunction1[String => Boolean, DataOverviewReader] {
      override def apply(filter: String => Boolean): DataOverviewReader = persistenceModule.persistenceDIModule.dataOverviewReader(filter)
    }

    implicit val weightProviderFormat: RootJsonFormat[WeightProvider[String]] = StringWeightProviderFormat(
      persistenceModule.persistenceDIModule.reader
    )

    implicit val orderedValuesJsonProtocol: OrderedValuesJsonProtocol = OrderedValuesJsonProtocol(
      persistenceModule.persistenceDIModule.reader,
      dataOverviewReaderWithConditionFunc
    )

    implicit val orderedMultiValuesJsonProtocol: OrderedMultiValuesJsonProtocol = OrderedMultiValuesJsonProtocol(
      persistenceModule.persistenceDIModule.reader,
      orderedValuesJsonProtocol
    )

    implicit val supplierJsonProtocol: SupplierJsonProtocol = json.SupplierJsonProtocol(
      persistenceModule.persistenceDIModule.reader,
      dataOverviewReaderWithSuffixFilterFunc,
      orderedValuesJsonProtocol,
      AppConfig.filepathToJudgementProvider,
      ClusterNodeObj
    )

    implicit val executionFormat: RootJsonFormat[Execution[Any]] = ExecutionFormat(
      persistenceModule.persistenceDIModule.reader,
      persistenceModule.persistenceDIModule.writer,
      AppProperties.config.metricDocumentFormatsMap,
      dataOverviewReaderWithRegexFilterFunc,
      weightProviderFormat,
      supplierJsonProtocol
    )

    implicit val parameterValueJsonProtocol: ParameterValuesJsonProtocol = ParameterValuesJsonProtocol(
      supplierJsonProtocol
    )

    implicit val queryBasedSearchEvaluationFormat: QueryBasedSearchEvaluationFormat = QueryBasedSearchEvaluationFormat(
      parameterValueJsonProtocol
    )

    implicit val resourceDirectiveJsonProtocol: ResourceDirectiveJsonProtocol = ResourceDirectiveJsonProtocol(supplierJsonProtocol)

    implicit val searchEvaluationJsonFormat: RootJsonFormat[SearchEvaluationDefinition] =
      SearchEvaluationJsonProtocol.SearchEvaluationFormat(
        executionFormat,
        resourceDirectiveJsonProtocol,
        parameterValueJsonProtocol
      )

    implicit val generatorJsonProtocol: IndexedGeneratorJsonProtocol = IndexedGeneratorJsonProtocol(
      dataOverviewReaderWithSuffixFilterFunc
    )

    implicit val mappingSupplierJsonProtocol: MappingSupplierJsonProtocol = MappingSupplierJsonProtocol(
      persistenceModule.persistenceDIModule.reader,
      dataOverviewReaderWithSuffixFilterFunc,
      generatorJsonProtocol
    )

    implicit val modifierMappersJsonProtocol: ModifierMappersJsonProtocol = ModifierMappersJsonProtocol(
      generatorJsonProtocol,
      mappingSupplierJsonProtocol
    )

    implicit val modifierGeneratorProviderJsonProtocol: ModifierGeneratorProviderJsonProtocol = ModifierGeneratorProviderJsonProtocol(generatorJsonProtocol,
      orderedMultiValuesJsonProtocol,
      modifierMappersJsonProtocol)

    implicit val seqModifierGeneratorJsonProtocol: SeqModifierGeneratorJsonProtocol = SeqModifierGeneratorJsonProtocol(
      modifierGeneratorProviderJsonProtocol
    )

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
