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


package de.awagen.kolibri.fleet.zio.metrics

import zio.metrics.MetricKeyType.Counter
import zio.{Chunk, Task, ZIO}
import zio.metrics._

import java.lang.management.ManagementFactory
import java.time.temporal.ChronoUnit


object Metrics {

  object MetricTypes {
    val taskManageCycleInvokeCount = Metric.counter("kolibri_task_manage_invoke_count")
      .fromConst(1)
  }

  object CalculationsWithMetrics {

    /**
     * Effect to calculate memory usage.
     *
     * To set a gauge metric, can just prepend the effect with  '@@ Metric.gauge("kolibri_memory_usage")' or similar
     */
    def memoryUsage: ZIO[Any, Nothing, Double] = {
      import java.lang.Runtime._
      ZIO
        .succeed(getRuntime.totalMemory() - getRuntime.freeMemory())
        .map(_ / (1024.0 * 1024.0))
    }

    /**
     * Regarding distinct jvm management beans and provided info:
     * e.g https://www.baeldung.com/java-metrics
     *
     * To set gauge metric, can just prepend the effect with '@@ Metric.gauge("kolibri_avg_load")' or similar
     */
    def avgSystemLoad: ZIO[Any, Nothing, Double] = {
      val bean = ManagementFactory.getOperatingSystemMXBean
      ZIO.succeed(bean.getSystemLoadAverage)
    }

    def getAvailableProcessors: Task[Int] = {
      ZIO.attempt(ManagementFactory.getOperatingSystemMXBean.getAvailableProcessors)
    }

    /**
     * Initial usage: heap memory the JVM requests from the OS on startup
     * Value given in MB
     */
    def getInitialHeapMemory: Task[Double] = {
      ZIO.attempt({
        ManagementFactory.getMemoryMXBean.getHeapMemoryUsage.getInit
      }).map(_ / (1024.0 * 1024.0))
    }

    /**
     * Value given in MB
     */
    def getInitialNonHeapMemory: Task[Double] = {
      ZIO.attempt({
        ManagementFactory.getMemoryMXBean.getNonHeapMemoryUsage.getInit
      }).map(_ / (1024.0 * 1024.0))
    }

    /**
     * Current non-heap memory used
     * Value given in MB
     */
    def getUsedHeapMemory: Task[Double] = {
      ZIO.attempt({
        ManagementFactory.getMemoryMXBean.getHeapMemoryUsage.getUsed
      }).map(_ / (1024.0 * 1024.0))
    }

    /**
     * Value given in MB
     */
    def getUsedNonHeapMemory: Task[Double] = {
      ZIO.attempt({
        ManagementFactory.getMemoryMXBean.getNonHeapMemoryUsage.getUsed
      }).map(_ / (1024.0 * 1024.0))
    }

    /**
     * Heap memory guaranteed to be available to JVM.
     * Value given in MB
     */
    def getCommittedHeapMemory: Task[Double] = {
      ZIO.attempt({
        ManagementFactory.getMemoryMXBean.getHeapMemoryUsage.getCommitted
      }).map(_ / (1024.0 * 1024.0))
    }

    /**
     * Value given in MB
     */
    def getCommittedNonHeapMemory: Task[Double] = {
      ZIO.attempt({
        ManagementFactory.getMemoryMXBean.getNonHeapMemoryUsage.getCommitted
      }).map(_ / (1024.0 * 1024.0))
    }

    /**
     * Max heap memory available to the JVM.
     * Value given in MB.
     */
    def getMaxHeapMemory: Task[Double] = {
      ZIO.attempt({
        ManagementFactory.getMemoryMXBean.getHeapMemoryUsage.getMax
      }).map(_ / (1024.0 * 1024.0))
    }

    /**
     * Value given in MB.
     */
    def getMaxNonHeapMemory: Task[Double] = {
      ZIO.attempt({
        ManagementFactory.getMemoryMXBean.getNonHeapMemoryUsage.getMax
      }).map(_ / (1024.0 * 1024.0))
    }

    /**
     * Counter for requests to API endpoints provided by kolibri-fleet service.
     */
    def countAPIRequests(method: String, handler: String): Metric[Counter, Any, MetricState.Counter] =
      Metric.counterInt("countApiRequests").fromConst(1)
        .tagged(
          MetricLabel("method", method),
          MetricLabel("handler", handler)
        )

    /**
     * Counter for client requests to external services.
     */
    def countExternalRequests(method: String, host: String, contextPath: String, responseCode: Int): Metric[Counter, Any, MetricState.Counter] =
      Metric.counterInt("countClientRequests").fromConst(1)
        .tagged(
          MetricLabel("method", method),
          MetricLabel("host", host),
          MetricLabel("contextPath", contextPath),
          MetricLabel("responseCode", responseCode.toString)
        )

    def externalRequestTimer(method: String, host: String, contextPath: String): Metric[MetricKeyType.Histogram, zio.Duration, MetricState.Histogram] =
      Metric.timer(
        name = "externalRequestTimer",
        chronoUnit = ChronoUnit.MILLIS,
        boundaries = Chunk.fromIterable(Seq(2.0, 5.0)) ++ Chunk.iterate(10.0, 100)(_ + 20.0)
      ).tagged(
        MetricLabel("method", method),
        MetricLabel("host", host),
        MetricLabel("contextPath", contextPath)
      )
  }





}
