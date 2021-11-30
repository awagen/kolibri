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


package de.awagen.kolibri.base.usecase.searchopt.fixtures

object SolrResponseFixture {

  val solrResponse1: String = "{\n  \"responseHeader\":{\n    \"status\":0,\n    \"QTime\":13,\n    \"params\":{\n      \"wt\":\"json\",\n      \"q\":\"test1\"}},\n  \"response\":{\"numFound\":2,\"start\":0,\"docs\":[\n      {\n        \"id\":\"11\",\n        \"product_id\":\"12\",\n        \"title\":\"testtitle1\"},\n      {\n        \"id\":\"21\",\n        \"product_id\":\"22\",\n        \"title\":\"testtitle2\"}]\n  }\n}"
  val solrResponse2: String = "{\n  \"responseHeader\":{\n    \"status\":0,\n    \"QTime\":16,\n    \"params\":{\n      \"wt\":\"json\",\n      \"q\":\"test2\"}},\n  \"response\":{\"numFound\":3,\"start\":0,\"docs\":[\n      {\n        \"id\":\"31\",\n        \"product_id\":\"32\",\n        \"title\":\"testtitle3\"},\n      {\n        \"id\":\"41\",\n        \"product_id\":\"42\",\n        \"title\":\"testtitle4\"},\n      {\n        \"id\":\"51\",\n        \"product_id\":\"52\",\n        \"title\":\"testtitle5\"}]\n  }\n}"
  val solrResponse3: String = "{\n  \"responseHeader\":{\n    \"status\":0,\n    \"QTime\":11,\n    \"params\":{\n      \"wt\":\"json\",\n      \"q\":\"test3\"}},\n  \"response\":{\"numFound\":1,\"start\":0,\"docs\":[\n      {\n        \"id\":\"61\",\n        \"product_id\":\"62\",\n        \"title\":\"testtitle6\"}]\n  }\n}"
  val solrResponse4: String = "{\n  \"responseHeader\":{\n    \"status\":0,\n    \"QTime\":9,\n     \"params\":{\n      \"wt\":\"json\",\n      \"q\":\"test4\"}},\n  \"response\":{\"numFound\":0,\"start\":0,\"docs\":[]\n  }\n}"

}
