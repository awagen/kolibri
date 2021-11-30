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

import de.awagen.kolibri.base.processing.TestTaskHelper.{concatIdsTask, productIdResult}
import de.awagen.kolibri.base.processing.execution
import de.awagen.kolibri.base.processing.failure.TaskFailType.{NotYetStartedTask, TaskFailType}
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import de.awagen.kolibri.base.utils.TestHelper._
import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}


class SimpleSyncTaskSpec extends UnitTestSpec {

  "SimpleTask" should {

    "correctly check type" in {
      //given
      val taskFuture = execution.task
        .SimpleSyncTask[Future[String]](Seq.empty, TestTypedClass[Future[String]]("hi"), TestTypedClass[TaskFailType]("fail"), _ => Left(NotYetStartedTask))
      val taskPromise = SimpleSyncTask[Promise[String]](Seq.empty, TestTypedClass[Promise[String]]("hi"), TestTypedClass[TaskFailType]("fail"),
        _ => Left(NotYetStartedTask))
      val taskInt = execution.task.SimpleSyncTask[Int](Seq.empty, TestTypedClass[Int]("hi"), TestTypedClass[TaskFailType]("fail"), _ => Right(1))
      //when, then
      taskFuture.isFuture mustBe true
      taskFuture.isPromise mustBe false
      taskPromise.isFuture mustBe false
      taskPromise.isPromise mustBe true
      taskInt.isFuture mustBe false
      taskInt.isPromise mustBe false
    }


    "correctly be executed" in {
      // given
      val map: TypeTaggedMap = TypedMapStore(mutable.Map.empty)
      map.put(productIdResult, Seq("p1", "p2", "p3"))
      // when
      val generatedData: Either[TaskFailType, String] = concatIdsTask.func.apply(map)
      // then
      generatedData mustBe Right("p1,p2,p3")
    }

  }

}
