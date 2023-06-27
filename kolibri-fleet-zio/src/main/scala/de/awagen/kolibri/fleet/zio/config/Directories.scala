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


package de.awagen.kolibri.fleet.zio.config

import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.fleet.zio.config.AppProperties.config
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.FileFormats.{ClaimFileNameFormat, InProgressTaskFileNameFormat}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.Parts.JOB_ID
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.TaskTopics.TaskTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.TaskStates._

/**
 * NOTE: jobId in the below means [jobName]_[timePlacedInMillis] format
 */
object Directories {

  val JOB_DEFINITION_FILENAME = "job.json"

  object JobTopLevel {
    def topLevelOpenJobFolder: String = s"${config.openJobBaseFolder}"

    def folderForJob(jobId: String, isOpenJob: Boolean): String = {
      if (isOpenJob) s"$topLevelOpenJobFolder/$jobId"
      else s"${config.doneJobBaseFolder}/$jobId"
    }

    /**
     * Derive the file path to the actual job definition for a job
     */
    def jobNameToJobDefinitionFile(jobName: String) =
      s"${JobTopLevel.folderForJob(jobName, isOpenJob = true)}/$JOB_DEFINITION_FILENAME"
  }

  object Tasks {
    def jobTaskSubFolder(jobId: String, isOpenJob: Boolean): String = {
      s"${jobSubFolder(jobId, config.perJobTaskBaseFolder, isOpenJob)}"
    }
  }

  object Claims {
    def jobClaimSubFolder(jobId: String, isOpenJob: Boolean): String = {
      s"${jobSubFolder(jobId, config.perJobClaimBaseFolder, isOpenJob)}"
    }

    def getFullFilePathForClaimFile(jobId: String, batchNr: Int, taskTopic: TaskTopic): String = {
      val fileName = ClaimFileNameFormat.getFileName(
        Task(
          jobId,
          batchNr,
          AppProperties.config.node_hash,
          System.currentTimeMillis(),
          taskTopic
        ))
      s"${Claims.jobClaimSubFolder(jobId, isOpenJob = true)}/$fileName"
    }

    def claimNameToPath(name: String): String = {
      val parsedName: WeaklyTypedMap[String] = ClaimFileNameFormat.parse(name)
      val jobId = JOB_ID.parseFunc(parsedName.get(JOB_ID.namedClassTyped.name).get)
      s"${jobClaimSubFolder(jobId, isOpenJob = true)}/$name"
    }
  }

  object OpenTasks {
    def jobOpenTasksSubFolder(jobId: String, isOpenJob: Boolean): String = {
      s"${jobSubFolder(jobId, config.perJobOpenTaskBaseFolder, isOpenJob)}"
    }

    def jobNameAndBatchNrToBatchFile(jobName: String, batchNr: Int) =
      s"${OpenTasks.jobOpenTasksSubFolder(jobName, isOpenJob = true)}/$batchNr"
  }

  object DoneTasks {
    def jobDoneTasksSubFolder(jobId: String, isOpenJob: Boolean): String = {
      s"${jobSubFolder(jobId, config.perJobDoneTaskBaseFolder, isOpenJob)}"
    }

    def jobNameAndBatchNrToDoneFile(jobId: String, batchNr: Int, isOpenJob: Boolean): String = {
      s"${DoneTasks.jobDoneTasksSubFolder(jobId, isOpenJob)}/$batchNr"
    }
  }

  object InProgressTasks {
    def jobTasksInProgressStateSubFolder(jobId: String, isOpenJob: Boolean): String = {
      s"${jobSubFolder(jobId, config.perJobTaskProgressStateBaseFolder, isOpenJob)}"
    }

    def jobTasksInProgressStateForNodeSubFolder(jobId: String, nodeHash: String, isOpenJob: Boolean): String = {
      s"${InProgressTasks.jobTasksInProgressStateSubFolder(jobId, isOpenJob)}/$nodeHash"
    }

    def getInProgressFilePathForJob(jobId: String, batchNr: Int, nodeHash: String): String = {
      val fileName: String = InProgressTaskFileNameFormat.getFileName(batchNr)
      s"${InProgressTasks.jobTasksInProgressStateSubFolder(jobId, isOpenJob = true)}/$nodeHash/$fileName"
    }
  }

  object NodeStates {

    def nodeStateBaseFolder: String = AppProperties.config.nodeHealthBaseFolder

    /**
     * Get relative path for node state file
     */
    def nodeStateFile(nodeHash: String): String = {
      s"${Directories.NodeStates.nodeStateBaseFolder.stripSuffix("/")}/$nodeHash"
    }

  }

  def jobSubFolder(jobId: String, subFolder: String, isOpenJob: Boolean): String = {
    s"${JobTopLevel.folderForJob(jobId, isOpenJob)}/$subFolder"
  }


}
