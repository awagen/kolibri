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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.zio_di

import de.awagen.kolibri.fleet.zio.config.AppProperties._

object ZioDIConfig {


  /**
   * NOTE: jobId in the below means [jobName]_[timePlacedInMillis] format
   */
  object Directories {

    def folderForJob(jobId: String, isOpenJob: Boolean): String = {
      if (isOpenJob) s"${config.openJobBaseFolder}/$jobId"
      else s"${config.doneJobBaseFolder}/$jobId"
    }

    def jobSubFolder(jobId: String, subFolder: String, isOpenJob: Boolean): String = {
      s"${folderForJob(jobId, isOpenJob)}/$subFolder"
    }

    def jobTaskSubFolder(jobId: String, isOpenJob: Boolean): String = {
      s"${jobSubFolder(jobId, config.perJobTaskBaseFolder, isOpenJob)}"
    }

    def jobClaimSubFolder(jobId: String, isOpenJob: Boolean): String = {
      s"${jobSubFolder(jobId, config.perJobClaimBaseFolder, isOpenJob)}"
    }

    def jobOpenTasksSubFolder(jobId: String, isOpenJob: Boolean): String = {
      s"${jobSubFolder(jobId, config.perJobOpenTaskBaseFolder, isOpenJob)}"
    }

    def jobDoneTasksSubFolder(jobId: String, isOpenJob: Boolean): String = {
      s"${jobSubFolder(jobId, config.perJobDoneTaskBaseFolder, isOpenJob)}"
    }

    def jobTasksInProgressStateSubFolder(jobId: String, isOpenJob: Boolean): String = {
      s"${jobSubFolder(jobId, config.perJobTaskProgressStateBaseFolder, isOpenJob)}"
    }

  }

}
