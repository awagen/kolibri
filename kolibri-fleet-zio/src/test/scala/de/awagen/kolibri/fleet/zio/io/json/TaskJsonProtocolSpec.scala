/**
 * Copyright 2023 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio.io.json

import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.definitions.domain.Connections
import de.awagen.kolibri.fleet.zio.execution.TaskFactory.RequestJsonAndParseValuesTask
import de.awagen.kolibri.fleet.zio.execution.ZIOTask
import de.awagen.kolibri.fleet.zio.io.json.TaskJsonProtocol._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.requests.RequestMode
import de.awagen.kolibri.fleet.zio.testclasses.UnitTestSpec
import spray.json._

class TaskJsonProtocolSpec extends UnitTestSpec {

  val REQUEST_MODE_PLACEHOLDER = "##REQUEST_MODE_PLACEHOLDER"

  def requestAndParseTaskJson: String =
    """
      |{
      |"type": "REQUEST_PARSE",
      |"parsingConfig": {
      |  "selectors": [
      |    {
      |      "name": "productIds",
      |      "castType": "STRING",
      |      "selector": "\\ data \\ products \\\\ productId"
      |    },
      |    {
      |      "name": "numFound",
      |      "castType": "DOUBLE",
      |      "selector": "\\ data \\ numFound"
      |    }
      |  ]
      |},
      |"taggingConfig": {
      |  "requestTagger": {
      |    "type": "REQUEST_PARAMETER",
      |    "parameter": "query",
      |    "extend": false
      |  },
      |  "parsingResultTagger": {
      |    "type": "NOTHING"
      |  }
      |},
      |"connections": [
      |  {
      |    "host": "test-service-1",
      |    "port": 80,
      |    "useHttps": false
      |  },
      |  {
      |    "host": "test-service-2",
      |    "port": 81,
      |    "useHttps": false
      |  },
      |  {
      |    "host": "test-service-3",
      |    "port": 81,
      |    "useHttps": false
      |  }
      |],
      |"requestMode": "##REQUEST_MODE_PLACEHOLDER",
      |"contextPath": "testContextPath",
      |"fixedParams": {
      |  "k1": ["v1", "v2"]
      |},
      |"httpMethod": "GET",
      |"successKeyName": "successTestKey",
      |"failKeyName": "failTestKey"
      |}""".stripMargin


  val connection1 = Connections.Connection("test-service-1", 80, useHttps = false, None)
  val connection2 = Connections.Connection("test-service-2", 81, useHttps = false, None)
  val connection3 = Connections.Connection("test-service-3", 81, useHttps = false, None)


  "TaskJsonProtocol" must {

    "correctly parse sequence of request and parse tasks" in {
      // given, when
      val taskDefRequestAll: Seq[RequestJsonAndParseValuesTask] = requestAndParseTaskJson.replace(REQUEST_MODE_PLACEHOLDER, RequestMode.REQUEST_ALL_CONNECTIONS.toString).parseJson.convertTo[Seq[ZIOTask[WeaklyTypedMap[String]]]]
        .asInstanceOf[Seq[RequestJsonAndParseValuesTask]]
      val taskDefDistribute: Seq[RequestJsonAndParseValuesTask] = requestAndParseTaskJson.replace(REQUEST_MODE_PLACEHOLDER, RequestMode.DISTRIBUTE_LOAD.toString).parseJson.convertTo[Seq[ZIOTask[WeaklyTypedMap[String]]]]
        .asInstanceOf[Seq[RequestJsonAndParseValuesTask]]
      // then
      taskDefRequestAll.size mustBe 3
      taskDefDistribute.size mustBe 1
      taskDefRequestAll.head.connectionSupplier() mustBe connection1
      taskDefRequestAll(1).connectionSupplier() mustBe connection2
      taskDefRequestAll(2).connectionSupplier() mustBe connection3
      Set(connection1, connection2, connection3).contains(taskDefDistribute.head.connectionSupplier())
    }

  }

}
