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

import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.JobDefinition
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.actions.JobActions
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStateSnapshot.TimePlacedOrdering
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.OpenJobsSnapshot.jobActionFilter
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates


object OpenJobsSnapshot {

  /**
   * Filter of jobs based on actions derived from a set of JobDirectives.
   * These directives cover stopping processing for all nodes, just running on selected nodes,
   * stopping processing only for a certain set of nodes and the like
   */
  val jobActionFilter: JobStateSnapshot => Boolean = snapshot => {
    snapshot.actionForJob match {
      case JobActions.StopAllNodes =>
        false
      case JobActions.ProcessOnlyNode(node) =>
        AppProperties.config.node_hash == node
      case JobActions.ProcessAllNodes =>
        true
      case JobActions.StopNodes(nodes) =>
        !nodes.contains(AppProperties.config.node_hash)
      case _ => false
    }
  }

}

/**
 * Gives overview of jobs currently set for processing.
 * Keeps sorting to provide a sorted order in which
 * jobs are processed.
 */
case class OpenJobsSnapshot(jobStateSnapshots: Map[String, JobStateSnapshot],
                            jobOrdering: Ordering[JobStateSnapshot] = TimePlacedOrdering) {

  /**
   * overview of all available jobs with more detailled info
   */
  val allJobsSortedByPriority: Seq[JobStateSnapshot] = getJobsSortedByPriority(_ => true)
  /**
   * overview of all jobs as relevant for this particular node
   */
  val jobsForThisNodeSortedByPriority: Seq[JobStateSnapshot] = allJobsSortedByPriority.filter(jobActionFilter)
  /**
   * overview of all jobs that shall not be processed on this node. If processing is running, stop all related
   * activities and move the batches back to open state.
   */
  val jobsToBeIgnoredOnThisNode: Seq[JobStateSnapshot] = allJobsSortedByPriority.filter(snapshot => !jobActionFilter.apply(snapshot))

  /**
   * Sorting of job states according to passed ordering.
   * Priority decreases from head to tail.
   */
  private[state] def getJobsSortedByPriority(jobFilter: JobStateSnapshot => Boolean): Seq[JobStateSnapshot] = {
    implicit val ordering: Ordering[JobStateSnapshot] = jobOrdering
    jobStateSnapshots.values.toSeq.filter(jobFilter).sorted
  }

  /**
   * Pick the next n open batches to run following the priority sorting.
   * Per job (as per job definition) contains sequence of batch numbers in open state.
   * Thus if we ask for n open batches and the highest prio job has at least this many open
   * batches, the sequence will only contain a single tuple with the batch seq of size n.
   * Otherwise either contains other jobs as well or - if not enough open batches available
   * over all open jobs - return less than n batches.
   */
  def getNextNOpenBatches(n: Int, ignoreClaimedBatches: Boolean): Seq[(JobDefinition[_, _], Seq[Int])] = {
    var ignoreJobNameBatchTuples: Map[String, Set[Int]] = Map.empty
    if (ignoreClaimedBatches) {
      ignoreJobNameBatchTuples = jobsForThisNodeSortedByPriority.map(x => (x.jobId, x.batchesToProcessingClaimNodes.keys.toSet)).toMap
    }
    val selectedBatches: Seq[(String, Int)] = jobsForThisNodeSortedByPriority
      .flatMap(x => {
        x.batchesToState
          .toSeq
          // only pick batches in open state and only those not marked to be ignored since they were already claimed
          .filter(y => {
            y._2 == BatchProcessingStates.Open &&
              ignoreJobNameBatchTuples.get(x.jobId).forall(ignoreBatches => !ignoreBatches.contains(y._1))
          })
          .map(z => (x.jobId, z._1))
      })
      .take(n)

    val mapping: Map[String, Seq[Int]] = selectedBatches.foldLeft(Map.empty[String, Seq[Int]])((oldMap, jobBatchTuple) => {
      oldMap + (jobBatchTuple._1 -> (oldMap.getOrElse[Seq[Int]](jobBatchTuple._1, Seq.empty) :+ jobBatchTuple._2))
    })
    val jobOrder = selectedBatches.map(x => x._1).distinct
    jobOrder.map(jobName => (jobStateSnapshots(jobName).jobDefinition, mapping(jobName)))
  }

  def getExistingClaimsForNode(nodeHash: String): Map[String, Iterable[Int]] = {
    jobStateSnapshots.values.map(jobState => {
      val batches = jobState.batchesToProcessingClaimNodes.filter(batchNrToClaimingNodes => {
        batchNrToClaimingNodes._2.contains(nodeHash)
      }).keys
      (jobState.jobId, batches)
    }).toMap
  }

}