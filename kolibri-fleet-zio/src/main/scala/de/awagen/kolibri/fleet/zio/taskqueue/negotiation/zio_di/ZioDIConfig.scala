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


  object Directories {
    def baseJobFolder(jobId: String): String = {
      s"${config.jobBaseFolder}/$jobId"
    }

    def baseTaskFolder(jobId: String): String = {
      s"${baseJobFolder(jobId)}/${config.taskBaseFolder}"
    }

    def jobToClaimSubFolder(jobId: String): String = {
      s"${baseTaskFolder(jobId)}/${config.taskClaimSubFolder}"
    }

    def jobToOpenTaskSubFolder(jobId: String): String = {
      s"${baseTaskFolder(jobId)}/${config.openTaskSubFolder}"
    }

    def jobToTaskProcessStatusSubFolder(jobId: String): String = {
      s"${baseTaskFolder(jobId)}/${config.taskProgressStateSubFolder}"
    }

    def jobToInProcessTaskSubFolder(jobId: String): String = {
      s"${baseTaskFolder(jobId)}/${config.taskInProgressSubFolder}"
    }
  }

}
