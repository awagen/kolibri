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

package de.awagen.kolibri.datatypes

import java.util.concurrent.atomic.AtomicReference

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

import scala.collection.immutable.Range
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._


class ConcurrentUpdateMapOpsSpec extends UnitTestSpec {

  implicit val ec: ExecutionContext =  scala.concurrent.ExecutionContext.global

  "ConcurrentUpdateMapOps" should {

    "perform all updates" in {
      // given
      val ref: AtomicReference[Map[String, String]] = new AtomicReference[Map[String, String]](Map.empty)
      // when
      val results: Seq[Future[Unit]] = Range(1,1000,1).map(x => {
        Future {ConcurrentUpdateMapOps.updateMapEntry[String, String](ref, y => y +  (s"k$x" -> s"v$x"))}
      })
      Await.result(Future.sequence(results), 1 second)
      // then
      Range(1,1000,1).map(x => ref.get().get(s"k$x")).exists(x => x.isEmpty) mustBe false
      Range(1,1000,1).exists(x => ref.get()(s"k$x") != s"v$x") mustBe false
    }

    "perform updates only if keys dont exist" in {
      // given
      val ref: AtomicReference[Map[String, String]] = new AtomicReference[Map[String, String]](Map.empty)
      val results1: Seq[Future[Unit]] = Range(1,1000,1).map(x => {
        Future {ConcurrentUpdateMapOps.updateMapEntry[String, String](ref, y => y +  (s"k$x" -> s"v$x"))}
      })
      Await.result(Future.sequence(results1), 1 second)
      // when
      val results2: Seq[Future[Unit]] = Range(1,1000,1).map(x => {
        Future {ConcurrentUpdateMapOps.updateMapEntryIfKeyNotExists[String, String](ref, s"k$x", s"v${x + 1000}")}
      })
      Await.result(Future.sequence(results2), 1 second)
      // then
      Range(1,1000,1).exists(x => ref.get()(s"k$x") != s"v$x") mustBe false
    }
  }

}
