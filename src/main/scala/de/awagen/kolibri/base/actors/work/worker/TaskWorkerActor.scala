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

package de.awagen.kolibri.base.actors.work.worker

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import de.awagen.kolibri.base.actors.work.worker.JobPartIdentifiers.JobPartIdentifier
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.{BadCorn, ProcessingMessage}
import de.awagen.kolibri.base.actors.work.worker.TaskWorkerActor._
import de.awagen.kolibri.base.config.AppConfig.config.kolibriDispatcherName
import de.awagen.kolibri.base.processing.execution.task.Task
import de.awagen.kolibri.base.processing.execution.task.TaskStates.{Done, Running, TaskState}
import de.awagen.kolibri.base.processing.failure.TaskFailType.{FailedByException, MissingResultKey, TaskFailType}
import de.awagen.kolibri.datatypes.ClassTyped
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.tagging.TagType._
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}


object TaskWorkerActor {

  def props[T]: Props = Props[TaskWorkerActor[T]]

  case class ProcessTasks[T](data: TypeTaggedMap with TaggedWithType[Tag],
                             tasks: Seq[Task[_]],
                             finalResultKey: ClassTyped[ProcessingMessage[T]],
                             identifier: JobPartIdentifier)
    extends InternalProcessingCmd

  sealed trait InternalProcessingCmd extends KolibriSerializable

  case class ContinueProcessing[T](lastValue: T) extends InternalProcessingCmd

  case class ProcessingFailed(e: Throwable) extends InternalProcessingCmd

  case class TaskFailed(t: TaskFailType) extends InternalProcessingCmd

}

/**
  * Worker executing a task list. Tasks are processed one by one to allow sending PoisonPill between tasks
  * to make sure processing can be stopped inbetween tasks (e.g in case supervisor is stopped)
  *
  * @tparam T
  */
class TaskWorkerActor[T] extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.system.dispatchers.lookup(kolibriDispatcherName)

  var taskStates: Vector[TaskState] = Vector.empty[TaskState]
  var currentTaskIndex: Int = 0
  var data: TypeTaggedMap with TaggedWithType[Tag] = _
  var tasks: Seq[Task[_]] = _
  var executionRequestor: ActorRef = _
  var resultKey: ClassTyped[ProcessingMessage[T]] = _
  var partIdentifier: JobPartIdentifier = _


  def process(data: TypeTaggedMap, tasks: Seq[Task[_]]): Unit = {
    val currentTaskState: TaskState = tasks(currentTaskIndex).start(data)
    taskStates = taskStates :+ currentTaskState
    currentTaskState match {
      case Done(Left(e)) =>
        self ! TaskFailed(e)
      case Done(Right(value)) =>
        self ! ContinueProcessing(value)
      case Running(future) =>
        future.onComplete({
          case Success(value) =>
            self ! ContinueProcessing(value)
          case Failure(value) =>
            self ! ProcessingFailed(value)
        })
    }
  }

  val waitingForWork: Receive = {
    case e@ProcessTasks(data, tasks, resultKey, identifier) if e.isInstanceOf[ProcessTasks[T]] =>
      this.executionRequestor = sender()
      this.data = data
      this.tasks = tasks
      this.resultKey = resultKey.asInstanceOf[ClassTyped[ProcessingMessage[T]]]
      this.partIdentifier = identifier
      context become processing
      process(data, tasks)
    case msg =>
      log.warning(s"waiting for task to process, but received message $msg, ignoring")
  }

  val processing: Receive = {
    case ContinueProcessing(value) =>
      taskStates = taskStates.updated(taskStates.size - 1, Done(Right(value)))
      currentTaskIndex += 1
      if (currentTaskIndex >= tasks.size) {
        val result: ProcessingMessage[Any] = data.get(resultKey) match {
          case Some(value) =>
            value
          case None =>
            BadCorn(MissingResultKey(resultKey))
        }
        result.addTags(AGGREGATION, this.data.getTagsForType(AGGREGATION))
        executionRequestor ! result
        self ! PoisonPill
      }
      else {
        process(data, tasks)
      }
    case ProcessingFailed(throwable) =>
      taskStates = taskStates.updated(taskStates.size - 1, Done(Left(FailedByException(throwable))))
      val response = BadCorn(FailedByException(throwable)).withTags(AGGREGATION, this.data.getTagsForType(AGGREGATION))
      executionRequestor ! response
      self ! PoisonPill
    case TaskFailed(failType) =>
      taskStates = taskStates.updated(taskStates.size - 1, Done(Left(failType)))
      val response = BadCorn(failType).withTags(AGGREGATION, this.data.getTagsForType(AGGREGATION))
      executionRequestor ! response
      self ! PoisonPill
    case other =>
      log.warning(s"waiting to continue processing, but received message $other, ignoring")
  }

  override def receive: Receive = waitingForWork

}
