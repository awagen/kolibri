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

import de.awagen.kolibri.definitions.testclasses.UnitTestSpec
import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors.{PlainAndRecursiveSelector, PlainPathSelector}
import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors.{NamedAndTypedSelector, TypedJsonSeqSelector, TypedJsonSingleValueSelector}
import de.awagen.kolibri.datatypes.types.JsonTypeCast
import de.awagen.kolibri.storage.io.reader.LocalResourceFileReader

class JudgementProvidersSpec extends UnitTestSpec {

  val localResourceFileReader: LocalResourceFileReader = LocalResourceFileReader("", None, fromClassPath = true)

  "FileBasedJudgementProvider" must {

    "correctly read from JSON_LINES format" in {
      // given
      val jsonQuerySelector: NamedAndTypedSelector[Option[Any]] = TypedJsonSingleValueSelector(name = "query", selector = PlainPathSelector(Seq("query")), castType = JsonTypeCast.STRING)
      val jsonProductsSelector: NamedAndTypedSelector[Seq[Any]] = TypedJsonSeqSelector(name = "products", selector = PlainAndRecursiveSelector(recursiveSelectorKey = "productId", plainSelectorKeys = "products"), castType = JsonTypeCast.STRING)
      val jsonJudgementsSelector: NamedAndTypedSelector[Seq[Any]] = TypedJsonSeqSelector(name = "judgements", selector = PlainAndRecursiveSelector(recursiveSelectorKey = "score", plainSelectorKeys = "products"), castType = JsonTypeCast.DOUBLE)
      val queryProductDelimiter: String = "-"
      val judgementProvider = FileBasedJudgementProvider.createJsonLineBasedProvider(
        localResourceFileReader,
        "data/json_lines_judgements.txt",
        jsonQuerySelector,
        jsonProductsSelector,
        jsonJudgementsSelector,
        queryProductDelimiter)
      // when, then
      judgementProvider.retrieveJudgementsForTerm("q1") mustBe Map(
        "a" -> 0.43,
        "b" -> 0.55,
        "c" -> 0.22
      )
      judgementProvider.retrieveJudgementsForTerm("q2") mustBe Map(
        "aa" -> 0.30,
        "bb" -> 0.11
      )
      judgementProvider.retrieveJudgementsForTerm("q3") mustBe Map(
        "cc" -> 0.10
      )
    }

  }

}
