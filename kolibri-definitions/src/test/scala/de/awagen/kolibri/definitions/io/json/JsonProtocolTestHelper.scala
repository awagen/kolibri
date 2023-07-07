package de.awagen.kolibri.definitions.io.json

import de.awagen.kolibri.definitions.directives.RetrievalDirective
import de.awagen.kolibri.definitions.resources.{ResourceProvider, RetrievalError}
import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors.{PlainAndRecursiveSelector, PlainPathSelector}
import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors.{NamedAndTypedSelector, TypedJsonSeqSelector, TypedJsonSingleValueSelector}
import de.awagen.kolibri.definitions.usecase.searchopt.provider.{FileBasedJudgementProvider, JudgementProvider}
import de.awagen.kolibri.datatypes.types.JsonTypeCast
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, LocalResourceDirectoryReader, LocalResourceFileReader, Reader}

object JsonProtocolTestHelper {

  val localResourceReader: Reader[String, Seq[String]] = LocalResourceFileReader("/", None, fromClassPath = true)
  val localDataOverviewReader: DataOverviewReader = LocalResourceDirectoryReader("/")
  val generatorJsonProtocol: IndexedGeneratorJsonProtocol = IndexedGeneratorJsonProtocol(
    suffix => LocalResourceDirectoryReader("/", x => x.endsWith(suffix))
  )
  implicit val mappingSupplierJsonProtocol: MappingSupplierJsonProtocol = MappingSupplierJsonProtocol(
    localResourceReader,
    suffix => LocalResourceDirectoryReader("/", x => x.endsWith(suffix)),
    generatorJsonProtocol
  )

  implicit val orderedValuesJsonProtocol = OrderedValuesJsonProtocol(
    localResourceReader,
    cond => LocalResourceDirectoryReader("/", cond)
  )

  implicit val orderedMultiValuesJsonProtocol = OrderedMultiValuesJsonProtocol(
    localResourceReader,
    orderedValuesJsonProtocol
  )

  implicit val modifierMappersJsonProtocol = ModifierMappersJsonProtocol(
    generatorJsonProtocol,
    mappingSupplierJsonProtocol
  )

  implicit val modifierGeneratorJsonProtocol = ModifierGeneratorProviderJsonProtocol(
    generatorJsonProtocol,
    orderedMultiValuesJsonProtocol,
    modifierMappersJsonProtocol
  )

  val jsonQuerySelector: NamedAndTypedSelector[Option[Any]] = TypedJsonSingleValueSelector(name = "query", selector = PlainPathSelector(Seq("query")), castType = JsonTypeCast.STRING)
  val jsonProductsSelector: NamedAndTypedSelector[Seq[Any]] = TypedJsonSeqSelector(name = "products", selector = PlainAndRecursiveSelector(recursiveSelectorKey = "productId", plainSelectorKeys = "products"), castType = JsonTypeCast.STRING)
  val jsonJudgementsSelector: NamedAndTypedSelector[Seq[Any]] = TypedJsonSeqSelector(name = "judgements", selector = PlainAndRecursiveSelector(recursiveSelectorKey = "score", plainSelectorKeys = "products"), castType = JsonTypeCast.DOUBLE)
  val queryProductDelimiter: String = "-"
  val judgementProvider: SerializableFunction1[String, JudgementProvider[Double]] = filepath =>  FileBasedJudgementProvider.createJsonLineBasedProvider(
    localResourceReader,
    filepath,
    jsonQuerySelector,
    jsonProductsSelector,
    jsonJudgementsSelector,
    queryProductDelimiter)

  implicit val supplierJsonProtocol = SupplierJsonProtocol(
    localResourceReader,
    suffix => LocalResourceDirectoryReader("/", x => x.endsWith(suffix)),
    orderedValuesJsonProtocol,
    judgementProvider,
    new ResourceProvider {
      override def getResource[T](directive: RetrievalDirective.RetrievalDirective[T]): Either[RetrievalError[T], T] = null
    }
  )

  implicit val parameterValuesJsonProtocol = ParameterValuesJsonProtocol(
    supplierJsonProtocol
  )

}
