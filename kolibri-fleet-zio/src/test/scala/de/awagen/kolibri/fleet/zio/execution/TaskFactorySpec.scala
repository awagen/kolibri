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

import de.awagen.kolibri.datatypes.mutable.stores.{BaseWeaklyTypedMap, WeaklyTypedMap}
import de.awagen.kolibri.datatypes.stores.immutable.MetricRow
import de.awagen.kolibri.datatypes.types.NamedClassTyped
import de.awagen.kolibri.datatypes.values.MetricValue
import de.awagen.kolibri.definitions.processing.ProcessingMessages
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.modifiers.RequestTemplateBuilderModifiers
import de.awagen.kolibri.fleet.zio.execution.TaskFactory.RequestJsonAndParseValuesTask
import de.awagen.kolibri.fleet.zio.execution.TaskFactory.RequestJsonAndParseValuesTask.requestTemplateBuilderModifierKey
import de.awagen.kolibri.fleet.zio.execution.TaskTestObjects.{mergeTwoMetricRowsTask, parsingConfig, twoMapInputCalculationTask}
import de.awagen.kolibri.fleet.zio.resources.NodeResourceProvider
import zio.http.Client
import zio.test._
import zio.{Scope, ZIO}

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

object TaskFactorySpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("TaskFactorySpec")(

    test("execute request and parse response") {
      val clientMock: Client = TaskTestObjects.httpClientMock("""{"results": {"field1": "value1"}}""")
      val initialMap = BaseWeaklyTypedMap(mutable.Map(
        requestTemplateBuilderModifierKey.name -> RequestTemplateBuilderModifiers
          .RequestParameterModifier(Map("param1" -> Seq("v1"), "q" -> Seq("q1")), replace = true)
      ))
      val task = TaskTestObjects.requestAndParseTask(clientMock, parsingConfig)
      for {
        value <- task.task(initialMap)
      } yield assertTrue(value.get[ProcessingMessage[WeaklyTypedMap[String]]](task.successKey).get.data.get[String]("field1").get == "value1")
    },

    test("calculate metrics") {
      // given
      val requestTemplate = TaskTestObjects.requestTemplate
      val typedMap = BaseWeaklyTypedMap(mutable.Map(
        "productIds" -> Seq("p5", "p2", "p1", "p4", "p3"),
        // NOTE: metrics calc needs the requestTemplate stored separately under the specific key below
        RequestJsonAndParseValuesTask.requestTemplateKey.name -> requestTemplate
      ))
      val initKey = NamedClassTyped[ProcessingMessage[WeaklyTypedMap[String]]]("initValues")
      val initialMap = BaseWeaklyTypedMap(mutable.Map(
        initKey.name -> ProcessingMessages.Corn(typedMap),
      ))
      // prepare directive to load judgement data before metrics calculation will retrieve it
      val judgementFileResourcePath: String = "/data/test_judgements.txt"
      val judgementResourceDirective = TaskTestObjects.judgementResourceDirective(judgementFileResourcePath)

      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
      // wait till resource is loaded
      Await.result(NodeResourceProvider.createResource(judgementResourceDirective).future, 10 seconds)
      val metricTask = TaskTestObjects.calculateMetricsTask(initKey.name, NodeResourceProvider)
      // when, then
      for {
        result <- metricTask.task(initialMap)
        _ <- ZIO.logDebug(s"result: $result")
      } yield assertTrue(result.get(metricTask.successKey).nonEmpty)

    },

    test("calculate result comparison metrics based on two WeaklyTypedMap[String] inputs") {
      // given
      val comparisonTask = twoMapInputCalculationTask()
      val inputMap1 = BaseWeaklyTypedMap(mutable.Map("productIds" -> Seq("p1", "p2", "p4", "p10")))
      val inputMap2 = BaseWeaklyTypedMap(mutable.Map("productIds" -> Seq("p2", "p1", "p11", "p10")))
      val initialMap = BaseWeaklyTypedMap(mutable.Map(
        "input1" -> ProcessingMessages.Corn(inputMap1),
        "input2" -> ProcessingMessages.Corn(inputMap2)
      ))
      // when, then
      for {
        result <- comparisonTask.task(initialMap)
        _ <- ZIO.logDebug(s"result: $result")
      } yield assert(result)(Assertion.assertion("correct result")(result => {
        val resultKey = comparisonTask.successKeyName
        val metricRowPM = result.get[ProcessingMessage[MetricRow]](resultKey).get
        metricRowPM.data.metrics("jaccard").biValue.value2.value == 0.60
      }))
    },

    test("Merge two metric rows into one"){
      // given
      val mergeTask = mergeTwoMetricRowsTask()
      val metricsSuccess1: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics1", 0.2, 1.0)
      val metricsSuccess2: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics2", 0.4, 1.0)
      val metricsSuccess3: MetricValue[Double] = MetricValue.createDoubleAvgSuccessSample("metrics3", 0.1, 1.0)
      val metricRow1 = MetricRow.emptyForParams(Map("p1" -> Seq("v1")))
      val updatedMetricRow1 = metricRow1.addFullMetricsSampleAndIncreaseSampleCount(metricsSuccess1, metricsSuccess2)
      val metricRow2 = MetricRow.emptyForParams(Map("p1" -> Seq("v1")))
      val updatedMetricRow2 = metricRow2.addFullMetricsSampleAndIncreaseSampleCount(metricsSuccess3)
      val initialMap = BaseWeaklyTypedMap(mutable.Map(
        "input1" -> ProcessingMessages.Corn(updatedMetricRow1),
        "input2" -> ProcessingMessages.Corn(updatedMetricRow2)
      ))
      // when, then
      for {
        result <- mergeTask.task(initialMap)
        _ <- ZIO.logDebug(s"result: $result")
      } yield assert(result.get[ProcessingMessage[MetricRow]]("metricComparisonResult") .get)(Assertion.assertion("correct merged result")(metricRowPM => {
        metricRowPM.data.metrics.keySet == Set("metrics1", "metrics2", "metrics3") &&
          metricRowPM.data.metrics("metrics1").biValue.value2.numSamples == 1 &&
          metricRowPM.data.metrics("metrics2").biValue.value2.numSamples == 1 &&
          metricRowPM.data.metrics("metrics3").biValue.value2.numSamples == 1
      }))
    }
  )
}
