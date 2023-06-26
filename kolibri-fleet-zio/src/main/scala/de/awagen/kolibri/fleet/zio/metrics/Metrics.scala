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

import zio.ZIO
import zio.metrics._

import java.lang.management.ManagementFactory


object Metrics {

  object MetricTypes {
    val taskManageCycleInvokeCount = Metric.counter("kolibri_task_manage_invoke_count")
      .fromConst(1)
  }

  object CalculationsWithMetrics {
    /**
     * Effect to calculate memory usage and on call update the memory usage metric
     * via aspect.
     */
    def memoryUsage: ZIO[Any, Nothing, Double] = {
      import java.lang.Runtime._
      ZIO
        .succeed(getRuntime.totalMemory() - getRuntime.freeMemory())
        .map(_ / (1024.0 * 1024.0)) @@ Metric.gauge("kolibri_memory_usage")
    }

    /**
     * Regarding distinct jvm management beans and provided info:
     * e.g https://www.baeldung.com/java-metrics
     */
    def avgSystemLoad: ZIO[Any, Nothing, Double] = {
      val bean = ManagementFactory.getOperatingSystemMXBean
      ZIO.succeed(bean.getSystemLoadAverage) @@ Metric.gauge("kolibri_avg_load")
    }

  }





}