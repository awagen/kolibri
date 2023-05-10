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

import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStateSnapshot.TimePlacedOrdering
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates

/**
 * Gives overview of jobs currently set for processing.
 * Keeps sorting to provide a sorted order in which
 * jobs are processed.
 */
case class OpenJobsSnapshot(jobStateSnapshots: Map[String, JobStateSnapshot],
                            jobOrdering: Ordering[JobStateSnapshot] = TimePlacedOrdering) {

  val jobsSortedByPriority: Seq[JobStateSnapshot] = getJobsSortedByPriority

  /**
   * Sorting of job states according to passed ordering.
   * Priority decreases from head to tail.
   */
  private[state] def getJobsSortedByPriority: Seq[JobStateSnapshot] = {
    implicit val ordering: Ordering[JobStateSnapshot] = jobOrdering
    jobStateSnapshots.values.toSeq.sorted
  }

  /**
   * Pick the next n open batches to run following the priority sorting.
   * Per job (as per job definition) contains sequence of batch numbers in open state.
   * Thus if we ask for n open batches and the highest prio job has at least this many open
   * batches, the sequence will only contain a single tuple with the batch seq of size n.
   * Otherwise either contains other jobs as well or - if not enough open batches available
   * over all open jobs - return less than n batches.
   */
  def getNextNOpenBatches(n: Int): Seq[(JobDefinition[_, _], Seq[Int])] = {
    val selectedBatches: Seq[(String, Int)] = jobsSortedByPriority
      .flatMap(x => {
        x.batchesWithState
          .toSeq
          .filter(y => y._2 == BatchProcessingStates.Open)
          .map(z => (x.jobId, z._1))
      })
      .take(n)

    val mapping: Map[String, Seq[Int]] = selectedBatches.foldLeft(Map.empty[String, Seq[Int]])((oldMap, jobBatchTuple) => {
      oldMap + (jobBatchTuple._1 -> (oldMap.getOrElse[Seq[Int]](jobBatchTuple._1, Seq.empty) :+ jobBatchTuple._2))
    })
    val jobOrder = selectedBatches.map(x => x._1).distinct
    jobOrder.map(jobName => (jobStateSnapshots(jobName).jobDefinition, mapping(jobName)))
  }

}