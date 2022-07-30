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

package de.awagen.kolibri.base.actors.work.aboveall

import akka.actor.ActorRef
import de.awagen.kolibri.base.actors.KolibriTestKit
import de.awagen.kolibri.base.actors.TestMessages.{generateProcessActorRunnableJobCmd, generateProcessActorRunnableTaskJobCmd}
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.FinishedJobEvent
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{ProcessingResult, ResultSummary}
import de.awagen.kolibri.datatypes.types.Types.WithCount

import scala.concurrent.duration._


class SupervisorActorSpec extends KolibriTestKit {

  "SupervisorActor" must {

    "correctly process ProcessActorRunnableJobCmd" in {
      // given
      val supervisor: ActorRef = system.actorOf(SupervisorActor.props(true))
      val msg: SupervisorActor.ProcessActorRunnableJobCmd[_, _, _, _ <: WithCount] = generateProcessActorRunnableJobCmd("testId1")
      // when
      supervisor ! msg
      // then
      val expectedResultSummary = ResultSummary(
        result = ProcessingResult.SUCCESS,
        nrOfBatchesTotal = 4,
        nrOfBatchesSentForProcessing = 4,
        nrOfResultsReceived = 4,
        failedBatches = Seq()
      )
      expectMsgPF(10 seconds) {
        case e: FinishedJobEvent if e.jobId == "testId1" && e.jobStatusInfo.resultSummary == expectedResultSummary => true
        case _ => false
      }
    }

    "correctly process ProcessActorRunnableTaskJobCmd" in {
      // given
      val supervisor: ActorRef = system.actorOf(SupervisorActor.props(true))
      val msg: SupervisorActor.ProcessActorRunnableTaskJobCmd[_ <: WithCount] = generateProcessActorRunnableTaskJobCmd("testId2")
      // when
      supervisor ! msg
      // then
      val expectedResultSummary = ResultSummary(
        result = ProcessingResult.SUCCESS,
        nrOfBatchesTotal = 5,
        nrOfBatchesSentForProcessing = 5,
        nrOfResultsReceived = 5,
        failedBatches = Seq()
      )
      expectMsgPF(10 seconds) {
        case e: FinishedJobEvent if e.jobId == "testId2" && e.jobStatusInfo.resultSummary == expectedResultSummary => true
        case _ => false
      }
    }

  }

}
