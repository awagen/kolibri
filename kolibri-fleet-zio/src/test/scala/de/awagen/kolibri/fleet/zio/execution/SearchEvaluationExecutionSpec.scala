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


package de.awagen.kolibri.fleet.zio.execution

import de.awagen.kolibri.datatypes.metrics.aggregation.mutable.MetricAggregation
import de.awagen.kolibri.datatypes.tagging.Tags
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobBatch
import de.awagen.kolibri.fleet.zio.execution.JobMessagesImplicits._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.processing.TaskWorker
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.http.{Client, Headers, Method, Response}
import zio.test._
import zio.{Scope, ZIO, ZLayer}

object SearchEvaluationExecutionSpec extends ZIOSpecDefault {

  def layerToInstance[T:zio.Tag]: ZIO[T, Any, T] = for {
    inst <- ZIO.service[T]
  } yield inst

  val exampleResponse = Response.json("""{"response": {"docs": [{"product_id": "p1"}, {"product_id": "p2"}]}}""")

  def spec: Spec[TestEnvironment with Scope, Any] = suite("SearchEvaluationExecutionSpec")(

    test("correctly execute tasks") {
      // given
      val clientMock = mock[Client]
      when(clientMock.headers).thenReturn(Headers.empty)
      when(clientMock.method).thenReturn(Method.GET)
      when(clientMock.sslConfig).thenReturn(None)
      when(clientMock.url).thenReturn(null)
      when(clientMock.version).thenReturn(null)
      when(clientMock.socket(
        ArgumentMatchers.any(),
        ArgumentMatchers.any(),
        ArgumentMatchers.any(),
        ArgumentMatchers.any(),
      )(ArgumentMatchers.any())).thenReturn(ZIO.succeed(exampleResponse))
      when(clientMock.request(
        ArgumentMatchers.any(),
        ArgumentMatchers.any(),
        ArgumentMatchers.any(),
        ArgumentMatchers.any(),
        ArgumentMatchers.any(),
        ArgumentMatchers.any()
      )(ArgumentMatchers.any())).thenReturn(ZIO.succeed(exampleResponse))
      when(clientMock.request(
        ArgumentMatchers.any()
      )(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(ZIO.succeed(exampleResponse))
      // when
      for {
        jobDef <- TaskTestObjects.searchEvaluationDefinition.toJobDef.provide(ZLayer.succeed(clientMock))
        aggregatorRefAndFiber <- TaskWorker.work(JobBatch(jobDef, 0))
        _ <- aggregatorRefAndFiber._2.join
      }
      // then
      yield assert(aggregatorRefAndFiber._1)(Assertion.assertion("correct aggregation")(aggregator => {
        aggregator.aggregation.isInstanceOf[MetricAggregation[Tags.Tag]]
      }))
    }
  )
}
