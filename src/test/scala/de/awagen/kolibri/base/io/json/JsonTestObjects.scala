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


package de.awagen.kolibri.base.io.json

object JsonTestObjects {

  val ORDERED_MULTIVALUES_RUNNABLE_JOB: String =
    """{"jobId": "job1", "dataProvider": {"data": {"values":[{"name": "test", "values": [0.45, 0.32]}]}},
      | "batchGenerator": {"paramNameToSplitBy": "q"}, "transformer": "IDENTITY", "processingActorProps": null,
      | "expectationGenerators": "ONE_FOR_ONE", "returnType": "IGNORE_SINK",
      | "aggregatorSupplier": {"type": "METRIC_ROW_AGGREGATOR"},
      | "writer": {"writer": {"writer": {"directory": "/tmp"}, "format": {"type": "csv", "columnSeparator": "/"}, "pathSeparator": "/"}},
      | "allowedTimePerBatchInSeconds": 10, "allowedTimeForJobInSeconds": 20}""".stripMargin

  val ORDERED_MULTIVALUES_RUNNABLE_TASKJOB: String =
    """{"jobId": "job1", "dataProvider": {"data": {"values":[{"name": "test", "values": [0.45, 0.32]}]}},
      | "batchGenerator": {"paramNameToSplitBy": "q"}, "resultDataKey": "METRICS",
      | "tasks": ["NULLTASK"],
      | "aggregatorSupplier": {"type": "METRIC_ROW_AGGREGATOR"},
      | "writer": {"writer": {"writer": {"directory": "/tmp"}, "format": {"type": "csv", "columnSeparator": "/"}, "pathSeparator": "/"}},
      | "allowedTimePerBatchInSeconds": 10, "allowedTimeForJobInSeconds": 20}""".stripMargin

}
