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

package de.awagen.kolibri.fleet.akka.actors.work.worker

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import de.awagen.kolibri.base.processing.JobPartIdentifiers.JobPartIdentifier
import de.awagen.kolibri.base.processing.ProcessingMessages.{BadCorn, ProcessingMessage}
import de.awagen.kolibri.fleet.akka.actors.work.worker.TaskExecutionWorkerActor.{ContinueExecution, ProcessTaskExecution}
import de.awagen.kolibri.base.processing.execution.task.TaskStates.{Done, NoState, Running, TaskState}
import de.awagen.kolibri.base.processing.failure.TaskFailType.{EmptyMetrics, FailedByException, NotExistingTask, TaskFailType}
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.TagType._
import de.awagen.kolibri.fleet.akka.execution.TaskExecution

import scala.util.{Failure, Success}


object TaskExecutionWorkerActor {

  def props[T]: Props = Props[TaskExecutionWorkerActor[T]]

  case class ProcessTaskExecution[T](taskExecution: TaskExecution[T], identifier: JobPartIdentifier)
    extends KolibriSerializable

  case object ContinueExecution extends KolibriSerializable

}

class TaskExecutionWorkerActor[T] extends Actor with ActorLogging {

  import context.dispatcher

  var sendingActor: ActorRef = _
  var execution: TaskExecution[T] = _
  var partIdentifier: JobPartIdentifier = _

  val startState: Receive = {
    case e: ProcessingMessage[ProcessTaskExecution[T]] =>
      this.sendingActor = sender()
      this.execution = e.data.taskExecution
      this.partIdentifier = e.data.identifier
      context.become(processingState)
      self ! ContinueExecution
    case ProcessTaskExecution(taskExecution, identifier) =>
      this.sendingActor = sender()
      this.execution = taskExecution.asInstanceOf[TaskExecution[T]]
      this.partIdentifier = identifier
      context.become(processingState)
      self ! ContinueExecution
    case msg =>
      log.warning(s"waiting for task to process, but received message $msg, ignoring")
  }

  val processingState: Receive = {
    case ContinueExecution =>
      val result: TaskState = execution.processRemainingTasks
      result match {
        case Done(Left(failType: TaskFailType)) =>
          log.warning("failed task execution, failType: '{}'", failType)
          val response = BadCorn(failType).withTags(AGGREGATION, this.execution.currentData.getTagsForType(AGGREGATION))
          sendingActor ! response
          self ! PoisonPill
        case Done(Right(_)) =>
          val result: Option[ProcessingMessage[T]] = execution.currentData.get(execution.resultKey)
          result match {
            case Some(value) =>
              value.addTags(AGGREGATION, this.execution.currentData.getTagsForType(AGGREGATION))
              sendingActor ! value
            case None =>
              val response = BadCorn(EmptyMetrics)
              response.addTags(AGGREGATION, this.execution.currentData.getTagsForType(AGGREGATION))
              sendingActor ! response
          }
          self ! PoisonPill
        case Running(future) =>
          future.onComplete({
            case Success(_) =>
              self.tell(ContinueExecution, sendingActor)
            case Failure(value) =>
              val response = BadCorn(FailedByException(value)).withTags(AGGREGATION, this.execution.currentData.getTagsForType(AGGREGATION))
              self.tell(response, sendingActor)
          })
        case NoState =>
          log.warning(s"Process context seems to contain no tasks, no processing - tasks: ${execution.tasks}")
          val response = BadCorn(NotExistingTask).withTags(AGGREGATION, this.execution.currentData.getTagsForType(AGGREGATION))
          sendingActor ! response
          self ! PoisonPill
      }
  }

  override def receive: Receive = startState
}
