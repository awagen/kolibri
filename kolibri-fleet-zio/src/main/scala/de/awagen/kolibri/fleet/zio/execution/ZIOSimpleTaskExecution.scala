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

import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.TaskFailType
import zio.ZIO
import zio.stream.ZStream


case class ZIOSimpleTaskExecution[+T](initData: WeaklyTypedMap[String],
                                      tasks: Seq[ZIOTask[_]]) extends ZIOTaskExecution[T] {

  assert(tasks.nonEmpty)

  val allFailKeys: Seq[String] = tasks.map(x => x.failKey)


  override def hasFailed(executionStates: Seq[ExecutionState]): Boolean =
    executionStates.exists(state => state.isInstanceOf[Failed])

  override def wasSuccessful(executionStates: Seq[ExecutionState]): Boolean = !hasFailed(executionStates) &&
    executionStates.size == tasks.size

  /**
   * Execute full sequence of tasks till either all succeeded or
   * first task failed.
   */
  override def processAllTasks: zio.Task[(WeaklyTypedMap[String], Seq[ExecutionState])] = {
    ZStream.fromIterable(tasks)
      .runFoldZIO((initData, Seq.empty[ExecutionState]))({
        case state if state._1._2.exists(x => x.isInstanceOf[Failed]) =>
          ZIO.succeed(state._1)
        case state =>
          val currentTask = state._2
          val currentTaskIndex = state._1._2.size
          currentTask.task(state._1._1)
          .flatMap({
            case updatedMap if updatedMap.keySet.contains(tasks(currentTaskIndex).failKey) =>
              for {
                processedTask <- ZIO.attempt(tasks(currentTaskIndex))
                _ <- ZIO.logWarning(s"Task (index $currentTaskIndex) failed with fail reason: ${updatedMap.get(processedTask.failKey)}")
                result <- ZIO.succeed((updatedMap, state._1._2 ++ Seq(Failed(currentTaskIndex, updatedMap.get[ProcessingMessage[TaskFailType]](processedTask.failKey).get.data))))
              } yield result
            case updatedMap =>
              for {
                _ <- ZIO.logDebug(s"Task (index $currentTaskIndex) succeeded")
                result <- ZIO.succeed((updatedMap, state._1._2 ++ Seq(Success)))
              } yield result
          })
      })
  }
}
