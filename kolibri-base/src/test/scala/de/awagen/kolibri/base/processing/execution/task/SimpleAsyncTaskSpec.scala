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

package de.awagen.kolibri.base.processing.execution.task

import de.awagen.kolibri.base.processing.TestTaskHelper._
import de.awagen.kolibri.base.processing.execution.task.TaskStates.{Running, TaskState}
import de.awagen.kolibri.base.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore}
import de.awagen.kolibri.datatypes.types.ClassTyped

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Success


class SimpleAsyncTaskSpec extends UnitTestSpec {

  "SimpleAsyncTask" should {

    "correctly handle successful execution" in {
      // given
      implicit val ec: ExecutionContext = ExecutionContext.global
      val successKey: ClassTyped[Int] = new ClassTyped[Int]
      val failKey: ClassTyped[TaskFailType] = new ClassTyped[TaskFailType]
      val futureFunc: TypeTaggedMap => Future[Int] = x => Future.successful(6)
      val successHandler: (Int, TypeTaggedMap) => Unit = (x, y) => print(s"yay: $x, yay map: ${y.get(successKey)}")
      val throwableHandler: Throwable => Unit = x => ()
      val typeTaggedMap: TypeTaggedMap = TypedMapStore(mutable.Map.empty[ClassTyped[Any], Any])
      typeTaggedMap.put(productIdResult, Seq("test1", "test2"))
      val task = SimpleAsyncTask(
        prerequisites = Seq(productIdResult),
        successKey = successKey,
        failKey = failKey,
        futureFunc = futureFunc,
        successHandler = successHandler,
        failureHandler = throwableHandler
      )
      // when
      val state: TaskState = task.start(typeTaggedMap)
      val state1: TaskState = task.start(typeTaggedMap)
      // then
      val fut1 = state.asInstanceOf[Running[Int]].future
      val fut2 = state1.asInstanceOf[Running[Int]].future
      val resultFut: Future[(Int, Int)] = for {
        v1 <- fut1
        v2 <- fut2
      } yield (v1, v2)
      Await.result(resultFut, 100 millis)
      resultFut.value.get mustBe Success((6, 6))
    }
  }

}
