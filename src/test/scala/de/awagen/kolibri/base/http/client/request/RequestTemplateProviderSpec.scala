///**
//  * Copyright 2021 Andreas Wagenmann
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  * http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */
//
//package de.awagen.kolibri.base.http.client.request
//
//import de.awagen.kolibri.base.io.reader.LocalResourceFileReader
//import de.awagen.kolibri.base.testclasses.UnitTestSpec
//import de.awagen.kolibri.datatypes.multivalues.{GridOrderedMultiValues, OrderedMultiValues}
//import de.awagen.kolibri.datatypes.values.{DistinctValues, RangeValues}
//
//import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
//
//
//class RequestTemplateProviderSpec extends UnitTestSpec {
//
//  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
//
//  val testValues = List(RangeValues("p1", 0.0f, 1.0f, 0.1f), RangeValues("p2", 0.0f, 10.0f, 1.0f),
//    DistinctValues("p3", List("val1", "val2"))) //121 * 2 = 242 total combinations
//  val testOrderedMultiValues: OrderedMultiValues = GridOrderedMultiValues(testValues)
//  val QUERIES_FILE = "data/testjudgements.txt"
//
//  val provider: RequestContextProvider = new RequestContextProviderBuilder("groupId")
//    .withContextPath("some/context/path")
//    .withDefaultParams(Map("param1" -> Seq("value1")))
//    .withParameters(testOrderedMultiValues)
//    .build
//
//
//  "SolrRequestContextProvider" must {
//
//    "provide all elements" in {
//      //given,when
//      val allElements = provider.iterator().toList
//      //then
//      allElements.size mustBe 242
//    }
//
//    "provide all elements for batch" in {
//      //given, when
//      val batches = provider.splitIntoBatchesOfSize(20)
//      //then
//      batches.size mustBe 13
//      for (index <- batches.indices) {
//        if (index == batches.indices.last) batches(index).iterator().toList.size mustBe 2
//        else batches(index).iterator().toList.size mustBe 20
//      }
//    }
//
//    "generate all elements" in {
//      val parameter1: RangeValues[Float] = RangeValues[Float]("cf.rcw", 0.0f, 1.0f, 0.01f)
//      val parameter2: RangeValues[Float] = RangeValues[Float]("cf.predictw", 0.0f, 1.0f, 0.2f)
//      //      val queries = LocalResourceFileBasedValueProvider(QUERIES_FILE, "\u0001", 0)
//      val queryReader = LocalResourceFileReader(Some("\u0001", 0))
//      val queries = queryReader.read(QUERIES_FILE)
//      val queryValues = DistinctValues[String]("q", queries.take(3))
//      val allParameters = GridOrderedMultiValues(Seq(parameter1, parameter2, queryValues))
//      val provider = new RequestContextProviderBuilder("groupId")
//        .withContextPath("some/context/path")
//        .withDefaultParams(Map("param1" -> Seq("value1")))
//        .withParameters(allParameters)
//        .build
//      provider.iterator().toVector.size mustBe 101 * 6 * 3
//
//    }
//
//  }
//
//
//}
