package de.awagen.kolibri.fleet.akka.execution

import de.awagen.kolibri.base.processing.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.base.processing.execution.task.Task
import de.awagen.kolibri.base.processing.execution.task.TaskStates.TaskState
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.types.ClassTyped

import scala.concurrent.ExecutionContext

trait TaskExecution[+T] extends KolibriSerializable {

  def hasFailed: Boolean

  def wasSuccessful: Boolean

  def currentData: TypeTaggedMap with TaggedWithType

  val resultKey: ClassTyped[ProcessingMessage[T]]

  def tasks: Seq[Task[_]]

  def processRemainingTasks(implicit ec: ExecutionContext): TaskState


}
