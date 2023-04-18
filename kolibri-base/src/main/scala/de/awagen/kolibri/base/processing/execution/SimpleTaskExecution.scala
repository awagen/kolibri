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


package de.awagen.kolibri.base.processing.execution

import de.awagen.kolibri.base.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.base.processing.execution.task.Task
import de.awagen.kolibri.base.processing.execution.task.TaskStates.{Done, NoState, Running, TaskState}
import de.awagen.kolibri.base.processing.failure.TaskFailType.{FailedByException, MissingPrerequisites, TaskFailType}
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.types.ClassTyped
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * Task execution that executes tasks one by one, syncronous tasks in blocking way, asyncronous in non-blocking way.
 * Syncronous ones resolve to Done[Either[TaskFailType, _]], while asyncronous ones report Running
 *
 * @param currentData
 * @param tasks
 */
case class SimpleTaskExecution[+T](resultKey: ClassTyped[ProcessingMessage[T]], currentData: TypeTaggedMap with TaggedWithType, tasks: Seq[Task[_]]) extends TaskExecution[T] {

  assert(tasks.nonEmpty)

  private val logger = LoggerFactory.getLogger(classOf[SimpleTaskExecution[_]])

  private var taskResults: Vector[TaskState] = Vector.empty

  def hasNext: Boolean = taskResults.size < tasks.size

  def hasFailed: Boolean = taskResults.exists {
    case Done(e) if e.isInstanceOf[Left[TaskFailType, _]] => true
    case _ => false
  }

  def taskStillRunning: Boolean = taskResults.exists {
    case Running(_) => true
    case _ => false
  }

  def wasSuccessful: Boolean = !hasFailed && !taskStillRunning && taskResults.size == tasks.size

  /**
   * Process tasks till either all processed, processing of any task failed
   * or one is processed asyncronously (you need to continue processing then via callback)
   *
   * @param ec
   * @return
   */
  def processRemainingTasks(implicit ec: ExecutionContext): TaskState = {
    var taskState: TaskState = taskResults.lastOption.getOrElse(NoState)
    while (hasNext && !taskStillRunning && !hasFailed && !wasSuccessful) {
      taskState = executeNextAndStoreResult
    }
    taskState match {
      case running@Running(future) =>
        future.onComplete({
          case Success(value) =>
            taskResults = taskResults.updated(taskResults.size - 1, Done(Right(value)))
          case Failure(exception) =>
            taskResults = taskResults.updated(taskResults.size - 1, Done(Left(FailedByException(exception))))
        })
        running
      case state => state
    }
  }

  /**
   * NOTE: since we have a Seq of general Task[Any], retrieving the value here and trying to add to map will fail
   * the comparison check to the typed key within the task. Thus the executeAndAddToMapAndReturn method on the task
   * itself is called which handles the specific type of the single task correctly.
   * There might be a workaround for this.
   *
   * @return
   */
  private[this] def executeNextAndStoreResult(implicit ec: ExecutionContext): TaskState = {
    if (taskStillRunning) return taskResults.last
    if (wasSuccessful) {
      logger.warn("all tasks successfully executed, doing nothing")
      return taskResults.last
    }
    val newTaskIndex = taskResults.size
    if (hasFailed) {
      logger.warn(s"this is a failed Execution of task with index ${newTaskIndex - 1}, next task can not be executed")
      return taskResults.last
    }
    val currentTask: Task[_] = tasks(newTaskIndex)
    val failedPrereqs: Seq[ClassTyped[_]] = currentTask.getFailedPrerequisites(currentData)
    if (failedPrereqs.nonEmpty) {
      logger.warn(s"failed prerequisites for task $currentTask: $failedPrereqs")
      val failType = MissingPrerequisites(failedPrereqs)
      currentData.put(currentTask.failKey, failType)
      taskResults = taskResults :+ Done(Left(failType))
      return Done(Left(failType))
    }
    val result: TaskState = currentTask.start(currentData)
    taskResults = taskResults :+ result
    result
  }

}
