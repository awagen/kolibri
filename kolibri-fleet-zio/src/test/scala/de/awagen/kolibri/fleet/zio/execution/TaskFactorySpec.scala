/**
 * Copyright 2022 Andreas Wagenmann
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


package de.awagen.kolibri.fleet.zio.execution

import de.awagen.kolibri.datatypes.immutable.stores.TypedMapStore
import de.awagen.kolibri.datatypes.types.JsonTypeCast
import de.awagen.kolibri.definitions.domain.Connections.Connection
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers
import de.awagen.kolibri.definitions.usecase.searchopt.parse.JsonSelectors.PlainPathSelector
import de.awagen.kolibri.definitions.usecase.searchopt.parse.ParsingConfig
import de.awagen.kolibri.definitions.usecase.searchopt.parse.TypedJsonSelectors.TypedJsonSingleValueSelector
import de.awagen.kolibri.fleet.zio.execution.TaskFactory.RequestJsonAndParseValuesTask
import de.awagen.kolibri.fleet.zio.execution.TaskFactory.RequestJsonAndParseValuesTask.requestTemplateBuilderModifierKey
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.http.{Client, Request, Response}
import zio.test.junit.JUnitRunnableSpec
import zio.test.{Spec, TestEnvironment, assertTrue}
import zio.{Scope, ZIO, ZLayer}

class TaskFactorySpec extends JUnitRunnableSpec {

  object TestObjects {

    val parsingConfig = ParsingConfig(Seq(
        TypedJsonSingleValueSelector("field1", PlainPathSelector(Seq("results","field1")), JsonTypeCast.STRING)
    ))
    val connectionSupplier: () => Connection = () => Connection(
      host = "testhost1",
      port = 80,
      useHttps = true,
      credentialsProvider = None)

    val contextPath = "testpath"

    val fixedParams = Map("param1" -> Seq("value1"))

    def httpClientMock: Client = mock[Client]

    def requestAndParseTask(clientMock: Client) = RequestJsonAndParseValuesTask(
      parsingConfig,
      connectionSupplier,
      contextPath,
      fixedParams,
      ZLayer.succeed(clientMock)
    )
  }

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("TaskFactorySpec") (

    test("execute request and parse response") {
      val clientMock: Client = TestObjects.httpClientMock
      when(clientMock.request(
        ArgumentMatchers.any[Request]
      )(ArgumentMatchers.any, ArgumentMatchers.any))
        .thenReturn(ZIO.attempt(Response.json("""{"results": {"field1": "value1"}}""")))
      val initialMap = TypedMapStore(Map(
        requestTemplateBuilderModifierKey -> RequestTemplateBuilderModifiers
          .RequestParameterModifier(Map("param1" -> Seq("v1")), replace=true)
      ))
      val task = TestObjects.requestAndParseTask(clientMock)
      for {
        value <- task.task(initialMap)
      } yield assertTrue(value.get(task.successKey).get.data._1.get[String]("field1").get == "value1")
    }

  )
}
