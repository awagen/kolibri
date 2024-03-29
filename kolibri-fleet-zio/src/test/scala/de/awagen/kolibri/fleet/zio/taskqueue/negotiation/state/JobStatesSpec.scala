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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state

import de.awagen.kolibri.fleet.zio.execution.JobDefinitions
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.BatchAggregationInfo
import de.awagen.kolibri.fleet.zio.execution.ZIOTasks.SimpleWaitTask
import de.awagen.kolibri.fleet.zio.execution.aggregation.Aggregators.MutableCountingAggregator
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.{JobStateSnapshot, OpenJobsSnapshot}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStatesSpec.TestData.jobStateSnapshot
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStatesSpec._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates
import de.awagen.kolibri.fleet.zio.testclasses.UnitTestSpec
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

object JobStatesSpec {

  val formatter: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  object TestData {
    def jobStateSnapshot(jobName: String, dateString: String, nrBatches: Int): JobStateSnapshot = JobStateSnapshot(
      jobName,
      formatter.parseDateTime(dateString).getMillis,
      JobDefinitions.simpleWaitJob(
        jobName,
        nrBatches,
        20L,
        1,
        BatchAggregationInfo(
          SimpleWaitTask.successKey,
          () => new MutableCountingAggregator(0, 0)
        )
      ),
      Set(JobDirectives.Process),
      Range(0, nrBatches, 1).map(x => (x, BatchProcessingStates.Open)).toMap
    )
  }

  val jobState1 = jobStateSnapshot("job1", "2020-03-01 01:10:32", 2)
  val jobState2 = jobStateSnapshot("job2", "2020-03-01 01:10:34", 2)
  val jobState3 = jobStateSnapshot("job3", "2020-03-02 01:10:34", 2)


}

class JobStatesSpec extends UnitTestSpec {

  "OpenJobsSnapshot" must {

    "sort jobs" in {
      // given
      val openJobsSnapshot = OpenJobsSnapshot(Seq(jobState3, jobState2, jobState1).map(x => (x.jobId, x)).toMap)
      // when
      val jobsSorted: Seq[JobStateSnapshot] = openJobsSnapshot.getJobsSortedByPriority(_ => true)
      // then
      jobsSorted.map(x => x.jobId) mustBe Seq("job1", "job2", "job3")
    }

    "provide n next batches to run" in {
      // given
      val openJobsSnapshot = OpenJobsSnapshot(Seq(jobState3, jobState2, jobState1).map(x => (x.jobId, x)).toMap)
      val allExpected = Seq(
        ("job1", Seq(0, 1)),
        ("job2", Seq(0, 1)),
        ("job3", Seq(0, 1))
      )
      // when
      val next6 = openJobsSnapshot.getNextNOpenBatches(6, Map.empty)
      val next10 = openJobsSnapshot.getNextNOpenBatches(10, Map.empty)
      val next3 = openJobsSnapshot.getNextNOpenBatches(3, Map.empty)
      // then
      next6.map(x => (x._1.jobName, x._2)) mustBe allExpected
      next10.map(x => (x._1.jobName, x._2)) mustBe allExpected
      next3.map(x => (x._1.jobName, x._2)) mustBe Seq(
        ("job1", Seq(0, 1)),
        ("job2", Seq(0))
      )
    }

    "ignore already claimed batches" in {
      // given
      val openJobsSnapshot = OpenJobsSnapshot(Seq(jobState3, jobState2, jobState1).map(x => (x.jobId, x)).toMap)
      val expectedUnclaimed = Seq(
        ("job2", Seq(0, 1)),
        ("job3", Seq(0, 1))
      )
      val ignoreBatches: Map[String, Set[Int]] = Map("job1" -> Set(0, 1))
      // when
      val next6 = openJobsSnapshot.getNextNOpenBatches(6, ignoreBatches)
      val next10 = openJobsSnapshot.getNextNOpenBatches(10, ignoreBatches)
      val next3 = openJobsSnapshot.getNextNOpenBatches(3, ignoreBatches)
      // then
      next6.map(x => (x._1.jobName, x._2)) mustBe expectedUnclaimed
      next10.map(x => (x._1.jobName, x._2)) mustBe expectedUnclaimed
      next3.map(x => (x._1.jobName, x._2)) mustBe Seq(
        ("job2", Seq(0, 1)),
        ("job3", Seq(0))
      )
    }
  }

}
