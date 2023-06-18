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

import de.awagen.kolibri.datatypes.immutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.types.ClassTyped
import de.awagen.kolibri.definitions.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.definitions.processing.failure.TaskFailType
import zio.{Task, ZIO}


case class ZIOSimpleTaskExecution[+T](initData: TypeTaggedMap,
                                      tasks: Seq[ZIOTask[_]]) extends ZIOTaskExecution[T] {

  assert(tasks.nonEmpty)

  val allFailKeys: Seq[ClassTyped[ProcessingMessage[TaskFailType.TaskFailType]]] = tasks.map(x => x.failKey)


  override def hasFailed(executionStates: Seq[ExecutionState]): Boolean =
    executionStates.exists(state => state.isInstanceOf[Failed])

  override def wasSuccessful(executionStates: Seq[ExecutionState]): Boolean = !hasFailed(executionStates) &&
    executionStates.size == tasks.size

  /**
   * Execute full sequence of tasks till either all succeeded or
   * first task failed.
   */
  override def processAllTasks: zio.Task[(TypeTaggedMap, Seq[ExecutionState])] = {
    val foldStartState: Task[(TypeTaggedMap, Seq[ExecutionState])] = tasks.head.task(initData)
      .flatMap({
        case e if e.keySet.contains(tasks.head.failKey) =>
          for {
            _ <- ZIO.logWarning(s"Task '1' failed with fail reason: ${e.get(tasks.head.failKey)}")
            result <- ZIO.succeed(e, Seq(Failed(0, e.get(tasks.head.failKey).get.data)))
          } yield result
        case e =>
          for {
            _ <- ZIO.logInfo("Task '1' succeeded")
            result <- ZIO.succeed((e, Seq(Success)))
          } yield result
      })
    val execution: Task[(TypeTaggedMap, Seq[ExecutionState])] = tasks.tail
      .foldLeft(foldStartState)((state, task) => {
        state.flatMap({
          case e if e._2.exists(x => x.isInstanceOf[Failed]) =>
            ZIO.succeed(e)
          case e =>
            task.task(e._1).flatMap({
              case v if v.keySet.contains(task.failKey) =>
                for {
                  _ <- ZIO.logWarning(s"State '$v' contains failKey '${task.failKey}'")
                  _ <- ZIO.logWarning(s"Task '${e._2.size + 1}' failed with fail reason: ${v.get(task.failKey)}")
                  result <- {
                    // TODO: the index in the Failed is wrong, correct with actual nr of task
                    ZIO.succeed((v, e._2 ++ Seq(Failed(0, v.get(task.failKey).get.data))))
                  }
                } yield result
              case v =>
                for {
                  _ <- ZIO.logInfo(s"Task '${e._2.size + 1}' succeeded")
                  result <- ZIO.succeed((v, e._2 ++ Seq(Success)))
                } yield result
            })
        })
      })
    execution
  }
}
