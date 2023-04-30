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

import de.awagen.kolibri.fleet.zio.config.AppConfig
import de.awagen.kolibri.fleet.zio.config.AppProperties._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.Jobs.Job
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimExerciseStatus.ClaimExerciseStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimFilingStatus.ClaimFilingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.ClaimVerifyStatus.ClaimVerifyStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ClaimStatus.{ClaimExerciseStatus, ClaimFilingStatus, ClaimVerifyStatus}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.ProcessUpdateStatus.ProcessUpdateStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.ClaimHandler.ClaimTopic.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.traits.{ClaimHandler, WorkStatusUpdater}
import spray.json.enrichAny
import zio.{Task, ULayer, ZIO, ZLayer}

import java.io.IOException

object ZioDIConfig {


  object Directories {
    def baseJobFolder(jobId: String): String = {
      s"${config.jobBaseFolder}/$jobId"
    }

    def baseTaskFolder(job: Job): String = {
      s"${baseJobFolder(job.jobId)}/${config.taskBaseFolder}"
    }

    def jobToClaimSubFolder(job: Job): String = {
      s"${baseTaskFolder(job)}/${config.taskClaimSubFolder}"
    }

    def jobToOpenTaskSubFolder(job: Job): String = {
      s"${baseTaskFolder(job)}/${config.openTaskSubFolder}"
    }

    def jobToTaskProcessStatusSubFolder(job: Job): String = {
      s"${baseTaskFolder(job)}/${config.taskProgressStateSubFolder}"
    }

    def jobToInProcessTaskSubFolder(job: Job): String = {
      s"${baseTaskFolder(job)}/${config.taskInProgressSubFolder}"
    }
  }

  object ClaimHandling {

    val CLAIM_PREFIX = "CLAIM"
    val TASK_PREFIX = "TASK"
    val FILE_NAME_PARTS_DELIMITER = "-"

    val claimHandler: ULayer[ClaimHandler] = ZLayer.succeed(
      new ClaimHandler {



        /**
         * Simply extracting the node-specific hash from the claim
         *
         * @param fileName
         * @return
         */
        def extractNodeHashFromClaimFileName(fileName: String): String = {
          fileName.split(FILE_NAME_PARTS_DELIMITER).last.split(".").head
        }

        def getExistingClaimsForJob(job: Job): Seq[String] = {
          AppConfig.persistenceModule.persistenceDIModule
            .dataOverviewReader(id => {
              val lastPath = id.split("/").last
              lastPath.startsWith(CLAIM_PREFIX) && lastPath.endsWith(".json")
            })
            .listResources(Directories.jobToClaimSubFolder(job), _ => true)
        }

        // TODO: could make single steps into effects and map them together
        override def fileClaim(job: Job, claimTopic: ClaimTopic): Task[ClaimFilingStatus] = ZIO.attemptBlockingIO {
          val existingClaimsForJob: Seq[String] = getExistingClaimsForJob(job)
          if (existingClaimsForJob.nonEmpty) ClaimFilingStatus.OTHER_CLAIM_EXISTS
          else {
            val jobString = job.toJson.toString()
            val targetFilePath = Files.jobToClaimFilePath(job)
            // TODO: bake this into a writer service and initialize all services centrally in
            // a DI config (zio layer or macWire)
            val persistResult: Either[Exception, _] = AppConfig.persistenceModule.persistenceDIModule.writer
              .write(jobString, targetFilePath)
            persistResult match {
              case _: Left[_, _] =>
                ClaimFilingStatus.PERSIST_FAIL
              case _ => ClaimFilingStatus.PERSIST_SUCCESS
            }
          }
        }

        /**
         * Verify whether a filed claim succeeded and if so (CLAIM_ACCEPTED)
         * indicates that the node can start processing
         * @param job - job identifier
         * @return
         */
        override def verifyClaim(job: Job, claimTopic: ClaimTopic): Task[ClaimVerifyStatus] = ZIO.attemptBlockingIO {
          val allExistingClaims: Seq[String] = getExistingClaimsForJob(job)
            .map(fileName => fileName.split("/").last)
            .sorted
          val allClaimNodeHashes = allExistingClaims
            .map(extractNodeHashFromClaimFileName)
          val node_hash = config.node_hash
          val nodeHadFiledClaim = allClaimNodeHashes.contains(node_hash)
          val nodeClaimIsFirst = allClaimNodeHashes.head == node_hash
          (nodeHadFiledClaim, nodeClaimIsFirst, allClaimNodeHashes.nonEmpty) match {
            case (_, _, false) => ClaimVerifyStatus.NO_CLAIM_EXISTS
            case (false, _, true) => ClaimVerifyStatus.NODE_CLAIM_DOES_NOT_EXIST
            case (_, false, true) => ClaimVerifyStatus.OTHER_CLAIMED_EARLIER
            case (true, true, true) => ClaimVerifyStatus.CLAIM_ACCEPTED
          }
        }

        /**
         * if winner hash corresponds to the current node, file an in-progress note,
         * remove all claims and start working, if not, ignore so that the node
         * that is the winner can start
         * @param job
         * @return
         */
        override def exerciseClaim(job: Job, claimTopic: ClaimTopic): Task[ClaimExerciseStatus] = {
          val executionEffect: ZIO[Any, IOException, (Either[Exception, _], Either[Exception, _], Seq[Either[Exception, _]])] = for {
            taskFile <- ZIO.attemptBlockingIO(
              AppConfig.persistenceModule.persistenceDIModule
                .dataOverviewReader(_ => true)
                .listResources(Directories.jobToOpenTaskSubFolder(job),
                  name => name == Files.openTaskFilePath(job).split("/").last)
                .last
            )
            taskFileContent <- ZIO.attemptBlockingIO(
              AppConfig.persistenceModule.persistenceDIModule.reader
                .read(taskFile).mkString("\n")
            )
            // move task to in progress tasks
            toInProgressWriteResult <- ZIO.attemptBlockingIO(
              AppConfig.persistenceModule.persistenceDIModule.writer
                .write(taskFileContent, Files.inProgressTaskFilePath(job))
            )
            taskFileDeleteResult <- ZIO.attemptBlockingIO(
              AppConfig.persistenceModule.persistenceDIModule.writer
                .delete(taskFile)
            )
            // remove all claims on the task from the claims folder
            deleteAllClaimsResult <- ZIO.attemptBlockingIO(
              getExistingClaimsForJob(job)
                .map(filePath => AppConfig.persistenceModule.persistenceDIModule.writer.delete(filePath))
            )
            // TODO: write in progress state for task into progress state folder
            // and add to in-progress jobs for the current node and start processing
          } yield (toInProgressWriteResult, taskFileDeleteResult, deleteAllClaimsResult)

          // TODO: obviously we should only say success here if the above execution
          // doesnt fail.
          // for that matter we could also split in the execution start and the state
          // cleanup part so that we could retry independently
          executionEffect.map(_ => ClaimExerciseStatus.EXERCISE_SUCCESS)

        }
      })

    def fileClaim(job: Job, claimTopic: ClaimTopic): ZIO[ClaimHandler, Throwable, ClaimFilingStatus] =
      ZIO.serviceWithZIO[ClaimHandler](_.fileClaim(job, claimTopic))

    def verifyClaim(job: Job, claimTopic: ClaimTopic): ZIO[ClaimHandler, Throwable, ClaimVerifyStatus] =
      ZIO.serviceWithZIO[ClaimHandler](_.verifyClaim(job, claimTopic))

    def exerciseClaim(job: Job, claimTopic: ClaimTopic): ZIO[ClaimHandler, Throwable, ClaimExerciseStatus] =
      ZIO.serviceWithZIO[ClaimHandler](_.exerciseClaim(job, claimTopic))

  }

  object WorkStatusUpdating {

    val workStatusUpdater: ULayer[WorkStatusUpdater] = ZLayer.succeed(
      new WorkStatusUpdater {
        override def update(): Task[ProcessUpdateStatus] = ???
      }
    )

    def update(): ZIO[WorkStatusUpdater, Throwable, ProcessUpdateStatus] =
      ZIO.serviceWithZIO[WorkStatusUpdater](_.update())
  }

}
