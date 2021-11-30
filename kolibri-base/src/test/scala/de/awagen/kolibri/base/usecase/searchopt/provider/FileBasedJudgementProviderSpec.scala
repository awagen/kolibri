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

import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.base.usecase.searchopt.parse.JsonSelectors.{PlainAndRecursiveSelector, PlainPathSelector}
import de.awagen.kolibri.base.usecase.searchopt.parse.TypedJsonSelectors.{SingleValueSelector, TypedJsonSeqSelector, TypedJsonSingleValueSelector}
import de.awagen.kolibri.datatypes.JsonTypeCast

class FileBasedJudgementProviderSpec extends UnitTestSpec {

  "FileBasedJudgementProvider" must {

    "correctly read from JSON_LINES format" in {
      // given
      val jsonQuerySelector: SingleValueSelector[Any] = TypedJsonSingleValueSelector(name = "query", selector = PlainPathSelector(Seq("query")), castType = JsonTypeCast.STRING)
      val jsonProductsSelector: TypedJsonSeqSelector = TypedJsonSeqSelector(name = "products", selector = PlainAndRecursiveSelector(recursiveSelectorKey = "productId", plainSelectorKeys = "products"), castType = JsonTypeCast.STRING)
      val jsonJudgementsSelector: TypedJsonSeqSelector = TypedJsonSeqSelector(name = "judgements", selector = PlainAndRecursiveSelector(recursiveSelectorKey = "score", plainSelectorKeys = "products"), castType = JsonTypeCast.DOUBLE)
      val queryProductDelimiter: String = "-"
      val judgementProvider = FileBasedJudgementProvider.createJsonLineBasedProvider(
        "data/json_lines_judgements.txt",
        jsonQuerySelector,
        jsonProductsSelector,
        jsonJudgementsSelector,
        queryProductDelimiter)
      // when
      val judgements: Map[String, Double] = judgementProvider.allJudgements
      // then
      judgements mustBe Map(
        "q1-a" -> 0.43,
        "q1-b" -> 0.55,
        "q1-c" -> 0.22,
        "q2-aa" -> 0.30,
        "q2-bb" -> 0.11,
        "q3-cc" -> 0.10
      )
    }

  }

}
