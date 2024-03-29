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

package de.awagen.kolibri.definitions.processing.execution

import de.awagen.kolibri.definitions.processing.TestTaskHelper._
import de.awagen.kolibri.definitions.processing.execution
import de.awagen.kolibri.definitions.processing.execution.task.{SimpleSyncTask, SyncTask}
import de.awagen.kolibri.definitions.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.mutable.stores.TypedMapStore
import de.awagen.kolibri.datatypes.tagging.TypeTaggedMapImplicits._
import de.awagen.kolibri.datatypes.types.ClassTyped

import scala.collection.mutable
import scala.concurrent.ExecutionContext


class SimpleTaskExecutionSpec extends UnitTestSpec {

  implicit val ec: ExecutionContext = ExecutionContext.global

  def prepareTaskExecution(tasks: Seq[SyncTask[_]], productSeq: Seq[String]): SimpleTaskExecution[String] = {
    val map = TypedMapStore(mutable.Map.empty[ClassTyped[Any], Any])
    map.put(productIdResult, productSeq)
    execution.SimpleTaskExecution(reversedIdKeyPM, map.toTaggedWithTypeMap, tasks)
  }

  "SimpleTaskExecution" should {

    "correctly execute all sync tasks" in {
      // given
      val tasks: Seq[SimpleSyncTask[String]] = Seq(concatIdsTask, reverseIdsTask)
      val execution: SimpleTaskExecution[String] = prepareTaskExecution(tasks, Seq("p3", "p4", "p21"))
      // when
      execution.processRemainingTasks
      // then
      execution.currentData.get(concatIdKey).get mustBe "p3,p4,p21"
      execution.currentData.get(reversedIdKey).get mustBe "12p,4p,3p"
    }

  }

}
