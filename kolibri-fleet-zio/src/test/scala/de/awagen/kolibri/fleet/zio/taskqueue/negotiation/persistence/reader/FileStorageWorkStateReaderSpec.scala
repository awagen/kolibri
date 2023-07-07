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

import de.awagen.kolibri.fleet.zio.config.AppProperties
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.{ProcessId, ProcessingInfo, ProcessingState, ProcessingStatus}
import de.awagen.kolibri.fleet.zio.testutils.TestObjects.workStateReader
import zio.Scope
import zio.test._

object FileStorageWorkStateReaderSpec extends ZIOSpecDefault {

  object TestObjects {

    val expectedState1 = ProcessingState(
      ProcessId("testJob1_3434839787", 0),
      ProcessingInfo(
        ProcessingStatus.QUEUED,
        100,
        0,
        AppProperties.config.node_hash,
        "2023-01-01 01:02:03"
      )
    )

    val expectedState2 = ProcessingState(
      ProcessId("testJob1_3434839787", 1),
      ProcessingInfo(
        ProcessingStatus.PLANNED,
        100,
        0,
        AppProperties.config.node_hash,
        "2023-01-01 01:02:03"
      )
    )

    val expectedState3 = ProcessingState(
      ProcessId("testJob1_3434839787", 2),
      ProcessingInfo(
        ProcessingStatus.QUEUED,
        100,
        0,
        "other1",
        "2023-01-01 01:02:03"
      )
    )

  }

  import TestObjects._

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("FileStorageWorkStateReaderSpec")(

    test("read process state from processId") {
      val reader = workStateReader
      for {
        processState <- reader.processIdToProcessState(ProcessId("testJob1_3434839787", 0), AppProperties.config.node_hash)
      } yield assert(processState)(Assertion.assertion("process state matches")(state => {
        state.get == expectedState1
      }))
    },

    test("read process states from processIds") {
      val reader = workStateReader
      for {
        processStatesNode1 <- reader.processIdsToProcessState(Seq(ProcessId("testJob1_3434839787", 0), ProcessId("testJob1_3434839787", 1)), AppProperties.config.node_hash)
        processStatesNode2 <- reader.processIdsToProcessState(Seq(ProcessId("testJob1_3434839787", 2)), "other1")
        invalidProcessNodeResult <- reader.processIdsToProcessState(Seq(ProcessId("testJob1_3434839787", 2)), "abc234")
      } yield assert(processStatesNode1)(Assertion.assertion("state1 match")(data => {
        data.size == 2 && data.head == expectedState1 && data(1) == expectedState2
      })) &&
        assert(processStatesNode2)(Assertion.assertion("state2 match")(data => {
          data.size == 1 && data.head == expectedState3
        })
      ) &&
        assert(invalidProcessNodeResult)(Assertion.assertion("state3 empty")(data => data.isEmpty))
    },

    test("read processIds for in-progress files for job") {
      val reader = workStateReader
      for {
        processIds <- reader.getInProgressIdsForCurrentNode(Set("testJob1_3434839787"))
      } yield assert(processIds)(Assertion.assertion("process ids match")(ids => {
        ids == Map("testJob1_3434839787" -> Set(
          ProcessId("testJob1_3434839787", 0),
          ProcessId("testJob1_3434839787", 1))
        )
      }))
    },

    test("read processIds for in-progress files for node and job") {
      val reader = workStateReader
      for {
        processIds <- reader.getInProgressIdsForNode(Set("testJob1_3434839787"), "other1")
      } yield assert(processIds)(Assertion.assertion("process ids match")(ids => {
        ids == Map("testJob1_3434839787" -> Set(
          ProcessId("testJob1_3434839787", 2))
        )
      }))
    },

    test("read processIds for in-progress files for all nodes") {
      val reader = workStateReader
      for {
        mapping <- reader.getInProgressIdsForAllNodes(Set("testJob1_3434839787"))
      } yield assert(mapping)(Assertion.assertion("process ids match")(mapping => {
        mapping.keySet == Set("abc234", "other1") &&
          mapping("abc234") == Map("testJob1_3434839787" -> Set(
            ProcessId("testJob1_3434839787", 0),
            ProcessId("testJob1_3434839787", 1))
          ) &&
          mapping("other1") == Map("testJob1_3434839787" -> Set(
            ProcessId("testJob1_3434839787", 2))
          )
      }))
    },

    test("read process states for in-progress files for job") {
      val reader = workStateReader
      for {
        inProgressStates <- reader.getInProgressStateForCurrentNode(Set("testJob1_3434839787"))
      } yield assert(inProgressStates)(Assertion.assertion("states match")(states => {
        states.keySet == Set("testJob1_3434839787") &&
          states("testJob1_3434839787") == Set(expectedState1, expectedState2)
      }))
    },

    test("read process states for in-progress files for all nodes") {
      val reader = workStateReader
      for {
        mapping <- reader.getInProgressStateForAllNodes(Set("testJob1_3434839787"))
      } yield assert(mapping)(Assertion.assertion("states match")(mapping => {
        mapping.keySet == Set("abc234", "other1") &&
          mapping("abc234") == Map("testJob1_3434839787" -> Set(expectedState1, expectedState2)) &&
          mapping("other1") == Map("testJob1_3434839787" -> Set(expectedState3))
      }))
    },

    test("get all node hashes with in-progress states") {
      val reader = workStateReader
      for {
        hashes <- reader.getAllNodeHashesWithInProgressStates(Set("testJob1_3434839787"))
      } yield assert(hashes)(Assertion.assertion("correct hashes")(hashes => hashes == Set("abc234", "other1")))
    }


  )

}
