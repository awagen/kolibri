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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader

import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.TaskTopics.JobTaskResetTask.typePrefix
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.TaskTopics._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.TaskStates.Task
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus.ClaimVerifyStatus


object ClaimReader {

  object TaskTopics {

    sealed trait TaskTopic {

      def id: String

    }

    /**
     * Topic for processing of a batch of the given job
     */
    case object JobTaskProcessingTask extends TaskTopic {

      val id: String = "JOB_TASK_PROCESSING"

      override def toString: String = id

    }

    /**
     * Topic for job wrap up task (meaning moving the job to done)
     */
    case object JobWrapUpTask extends TaskTopic {

      val id: String = "JOB_WRAP_UP"

      override def toString: String = id

    }

    /**
     * Task for resetting a batch processing state, that is: move batch back to open state and remove the
     * in-progress state
     */
    object JobTaskResetTask {

      val argumentDelimiter = "-"
      val typePrefix = "JOB_TASK_RESET"

      def fromString(str: String): JobTaskResetTask = {
        JobTaskResetTask(str.split(argumentDelimiter)(1))
      }

    }

    sealed case class JobTaskResetTask(nodeHash: String) extends TaskTopic {

      val id: String = typePrefix

      override def toString: String = s"$typePrefix-$nodeHash"

    }

    /**
     * Post processing if any is defined, e.g aggregating sub-results or the like
     */
    case object JobPostProcessing extends TaskTopic {

      val id: String = "JOB_POST_PROCESSING"

      override def toString: String = id

    }

    sealed case class NodeHealthRemovalTask(nodeHash: String) extends TaskTopic {

      val id: String = "NODE_HEALTH_REMOVE"

      override def toString: String = id
    }

    /**
     * Placeholder which basically corresponds to no task
     */
    case object Unknown extends TaskTopic {

      val id: String = "UNKNOWN"

      override def toString: String = id

    }

  }

  def stringToTaskTopic(str: String): TaskTopic = str match {
    case JobTaskProcessingTask.id => JobTaskProcessingTask
    case JobWrapUpTask.id => JobWrapUpTask
    case v if v.startsWith(JobTaskResetTask.typePrefix) => JobTaskResetTask.fromString(str)
    case _ => Unknown
  }

}

trait ClaimReader {

  def getAllClaims(jobIds: Set[String]): zio.Task[Set[Task]]

  def getAllClaimsForTopicAndJobIds(jobIds: Set[String], taskTopic: TaskTopic): zio.Task[Set[Task]]

  def getClaimsForJobAndTopic(jobId: String, taskTopic: TaskTopic): zio.Task[Seq[Task]]

  def getClaimsForBatchAndTopic(jobId: String, batchNr: Int, taskTopic: TaskTopic): zio.Task[Set[Task]]

  def getClaimsByCurrentNodeForTopicAndJobIds(taskTopic: TaskTopic, jobIds: Set[String]): zio.Task[Set[Task]]

  def verifyBatchClaim(jobId: String, batchNr: Int, taskTopic: TaskTopic): zio.Task[ClaimVerifyStatus]




}
