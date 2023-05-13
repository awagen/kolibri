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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.utils

import zio.stream.ZStream
import zio.{Queue, Task, ZIO}

object DataTypeUtils {

  /**
   * Try to add multiple elements to queue.
   * Deviates from the original queue.offerAll since we want the addition to stop on the
   * first fail instead of back-pressuring and adding later.
   * The returned sequence contains true for each element where offering to the queue
   * succeeded, and false where it failed. Note that there will be at most one
   * element with value false, and this would be the last element of the sequence,
   * since we abort further offerings in case an offering was not successful.
   */
  def addElementsToQueueIfEmptySlots[A](elements: Seq[A], queueRef: Queue[A]): Task[Seq[Boolean]] = {
    ZStream.fromIterable(elements)
      .mapZIO(e =>
        // we need to check here explicitly, since otherwise the fiber blocks
        // on offer till any place in the queue is available
        ZIO.ifZIO(queueRef.isFull)(
          onTrue = ZIO.succeed(false),
          onFalse = queueRef.offer(e)
        )
      )
      .mapZIO({
        case false => ZIO.fail(new RuntimeException("Failed adding element to queue"))
        case true => ZIO.succeed(true)
      })
      .orElse[Any, Exception, Boolean](ZStream.fromIterable[Boolean](Seq(false)))
      .runFold[Seq[Boolean]](Seq.empty)((oldSeq, newElement) => oldSeq :+ newElement)
  }

  def numFreeQueueSlots[A](queue: Queue[A]): Task[Int] = {
    queue.size.map(size => queue.capacity - size)
  }

}
