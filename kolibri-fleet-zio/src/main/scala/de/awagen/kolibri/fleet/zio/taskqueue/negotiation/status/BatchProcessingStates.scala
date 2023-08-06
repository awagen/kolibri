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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status

object BatchProcessingStates {

  sealed trait BatchProcessingStatus

  /**
   * Status indicating no processing of the batch has started yet
   */
  case object Open extends BatchProcessingStatus {

    override def toString: String = "OPEN"

  }

  /**
   * Status indicating batch is in progress on the given node
   */
  case class InProgress(nodeHash: String) extends BatchProcessingStatus {

    override def toString: String = s"INPROGRESS_$nodeHash"

  }

  /**
   * Status for successful completion of the batch
   */
  case object Done extends BatchProcessingStatus {

    override def toString: String = "DONE"

  }

  /**
   * In case the main batch processing failed (as opposed to post-processing actions).
   */
  case class Failed(reason: String) extends BatchProcessingStatus {

    override def toString: String = s"FAILED_$reason"

  }

  /**
   * State in case the processing itself was fine but defined post-actions
   * (additional post-processing tasks after the main batch processing) failed.
   */
  case object DoneWithFailedPostActions extends BatchProcessingStatus {

    override def toString: String = "DONE_WITH_FAILED_POST_ACTIONS"

  }

}
