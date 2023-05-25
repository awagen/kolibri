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


package de.awagen.kolibri.fleet.zio.execution

import de.awagen.kolibri.datatypes.immutable.stores.TypedMapStore
import de.awagen.kolibri.fleet.zio.execution.ZIOTasks.SimpleWaitTask
import zio._
import zio.test._


object ZIOSimpleTaskExecutionSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("ZIOSimpleTaskExecutionSpec")(

    test("correctly execute tasks") {
      // given
      val task: ZIOTask[Unit] = SimpleWaitTask(50)
      val taskExecution: ZIOSimpleTaskExecution[Unit] = ZIOSimpleTaskExecution(TypedMapStore.apply(Map.empty), Seq(task))
      // when, then
      for {
        result <- taskExecution.processAllTasks
      } yield assertTrue(!result._2.exists(x => x.isInstanceOf[Failed]))
    }
  )

}
