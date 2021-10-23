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

package de.awagen.kolibri.base.actors.work.manager

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor
import de.awagen.kolibri.base.actors.work.manager.JobManagerActor.{ACK, WorkerKilled}
import de.awagen.kolibri.base.actors.work.manager.WorkManagerActor.ExecutionType.{RUNNABLE, TASK, TASK_EXECUTION}
import de.awagen.kolibri.base.actors.work.manager.WorkManagerActor._
import de.awagen.kolibri.base.actors.work.worker.AggregatingActor.ReportResults
import de.awagen.kolibri.base.actors.work.worker.JobPartIdentifiers.JobPartIdentifier
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.base.actors.work.worker.TaskExecutionWorkerActor.ProcessTaskExecution
import de.awagen.kolibri.base.actors.work.worker.TaskWorkerActor.ProcessTasks
import de.awagen.kolibri.base.actors.work.worker.{RunnableExecutionActor, TaskExecutionWorkerActor, TaskWorkerActor}
import de.awagen.kolibri.base.config.AppProperties.config.kolibriDispatcherName
import de.awagen.kolibri.base.io.writer.Writers
import de.awagen.kolibri.base.processing.JobMessages.{SearchEvaluation, TestPiCalculation}
import de.awagen.kolibri.base.processing.JobMessagesImplicits._
import de.awagen.kolibri.base.processing.execution.TaskExecution
import de.awagen.kolibri.base.processing.execution.job.ActorRunnable
import de.awagen.kolibri.base.processing.execution.task.Task
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.RequestTemplateBuilderModifier
import de.awagen.kolibri.base.traits.Traits.WithBatchNr
import de.awagen.kolibri.datatypes.ClassTyped
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.metrics.aggregation.MetricAggregation
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.stores.MetricRow
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag

import scala.collection.mutable
import scala.concurrent.ExecutionContext


object WorkManagerActor {

  def props: Props = Props[WorkManagerActor].withDispatcher(kolibriDispatcherName)

  sealed trait WorkManagerMsg extends KolibriSerializable

  case class TasksWithTypedResult[T](data: TypeTaggedMap with TaggedWithType, tasks: Seq[Task[_]], finalResultKey: ClassTyped[ProcessingMessage[T]], partIdentifier: JobPartIdentifier) extends WorkManagerMsg

  case class TaskExecutionWithTypedResult[T](taskExecution: TaskExecution[T], partIdentifier: JobPartIdentifier) extends WorkManagerMsg

  object ExecutionType extends Enumeration with KolibriSerializable {
    val TASK, TASK_EXECUTION, RUNNABLE = Value
  }

  case class GetWorkerStatus(executionType: ExecutionType.Value, job: String, batchNr: Int) extends WorkManagerMsg

  case class JobBatchMsg[T](jobName: String, batchNr: Int, msg: T) extends WorkManagerMsg with WithBatchNr

}


class WorkManagerActor() extends Actor with ActorLogging with KolibriSerializable {

  var receivedBatchCount: Int = 0
  // record of worker key to the actual actor reference
  val workerKeyToActiveWorker: mutable.Map[String, ActorRef] = mutable.Map.empty
  // worker key to the respective job manager expecting the answer
  val workerKeyToJobManager: mutable.Map[String, ActorRef] = mutable.Map.empty
  // mapping of jobId to runnable generator to be able to get rid of serialization
  // issues compared to sending the ActorRunnables directly from JobManager
  // (WorkManager creates executing actors on the same node, thus the messages
  // are passed by reference)

  // every work manager is job specific, thus we only need to set the
  // generator of the runnables once. This actor is supposed to be utilized within
  // a JobManagerActor (thus will be killed when its parent is killed)
  var runnableGeneratorForJob: Option[IndexedGenerator[ActorRunnable[_, _, _, _]]] = None

  def workerKey(executionType: ExecutionType.Value, jobId: String, batchNr: Int): String = {
    s"${executionType.toString}_${jobId}_${batchNr}"
  }

  def jobIdFromKey(key: String): String = {
    val parts: Array[String] = key.split("_")
    if (parts.length < 2) "" else parts(1)
  }

  def batchNrFromKey(key: String): Int = {
    val parts: Array[String] = key.split("_")
    if (parts.length < 3) -1 else parts(2).toInt
  }

  /**
    * TaskWorkerActor and TaskExecutionWorkerActor both are sending results to the actor set as sender
    * (or this WorkManagerActor here in case the sender of the request here is not set as sender within
    * the tell-call). For ActorRunnable, there are several options. In case the Runnable is such that
    * it sends some message to an Actor for processing, the JobActorConfig passed needs to have
    * an ActorRef set for the RUNNABLE_SENDER key. Otherwise any response would be sent not to
    * RunnableExecutionActor processing it but to the Actor created to execute the graph
    * (implemented within Akka internals). The RunnableExecutionActor itself sends all messages it
    * receives after starting the processing to the sender set (that is, if RunnableExecutionActor
    * itself is set as RUNNABLE_SENDER and another actor as the sender of the message that started processing
    * in RunnableExecutionActor, the RunnableExecutionActor will also send those messages to the
    * given sender).
    * If an ActorRef is also set for the ACTOR_SINK key (JobActorConfig), the elements after transformation
    * (transformer.apply(x)) are also sent to this actor (otherwise ignored).
    * Do not set any ACTOR_SINK actorRef if those elements are not needed. Most of the time wed need some
    * other actor to do some calculation after transformer.apply was called and only need to be informed
    * about the result, which works the way descibed above (setting RUNNABLE_SENDER and sender).
    *
    * @return
    */
  override def receive: Receive = {
    case e: TasksWithTypedResult[_] =>
      val taskWorker: ActorRef = context.actorOf(TaskWorkerActor.props)
      workerKeyToActiveWorker.put(workerKey(TASK, e.partIdentifier.jobId, e.partIdentifier.batchNr), taskWorker)
      taskWorker.tell(ProcessTasks(e.data, e.tasks, e.finalResultKey, e.partIdentifier), sender())
    case e: TaskExecutionWithTypedResult[_] =>
      val taskExecutionWorker: ActorRef = context.actorOf(TaskExecutionWorkerActor.props)
      workerKeyToActiveWorker.put(workerKey(TASK_EXECUTION, e.partIdentifier.jobId, e.partIdentifier.batchNr), taskExecutionWorker)
      taskExecutionWorker.tell(ProcessTaskExecution(e.taskExecution, e.partIdentifier), sender())
    case e: JobBatchMsg[TestPiCalculation] if e.msg.isInstanceOf[TestPiCalculation] =>
      log.info("received TestPiCalculation msg")
      implicit val ec: ExecutionContext = context.system.dispatchers.lookup(kolibriDispatcherName)
      implicit val actorSystem: ActorSystem = context.system
      if (receivedBatchCount % 10 == 0) {
        log.info(s"received pi calc batch messages: $receivedBatchCount")
      }
      receivedBatchCount += 1
      if (runnableGeneratorForJob.isEmpty) {
        runnableGeneratorForJob = Some(e.msg.toRunnable.processElements)
      }
      val runnable: Option[ActorRunnable[_, _, _, _]] = runnableGeneratorForJob.flatMap(x => x.get(e.batchNr))
      runnable
        .map(x => distributeRunnable(x, None))
        .getOrElse(log.warning(s"job for message '$e' does not contain batch '${e.batchNr}', not executing anything for batch"))
    case e: JobBatchMsg[SearchEvaluation] if e.msg.isInstanceOf[SearchEvaluation] =>
      log.info("received SearchEvaluation msg")
      implicit val ec: ExecutionContext = context.system.dispatchers.lookup(kolibriDispatcherName)
      implicit val actorSystem: ActorSystem = context.system
      val runnableMsg: SupervisorActor.ProcessActorRunnableJobCmd[RequestTemplateBuilderModifier, MetricRow, MetricRow, MetricAggregation[Tag]] = e.msg.toRunnable
      val writerOpt: Option[Writers.Writer[MetricAggregation[Tag], Tag, _]] = Some(runnableMsg.writer)
      if (runnableGeneratorForJob.isEmpty) {
        runnableGeneratorForJob = Some(e.msg.toRunnable.processElements)
      }
      val runnable: Option[ActorRunnable[_, _, _, _]] = runnableGeneratorForJob.flatMap(x => x.get(e.batchNr))
      runnable
        .map(x => distributeRunnable(x, writerOpt))
        .getOrElse(log.warning(s"job for message '$e' does not contain batch '${e.batchNr}', not executing anything for batch"))
    case runnable: ActorRunnable[_, _, _, _] =>
      log.info("received generic runnable msg")
      distributeRunnable(runnable, None)
    case Terminated(actorRef: ActorRef) =>
      log.debug(s"received termination of actor: ${actorRef.path.toString}")
      val killedKeys: Seq[String] = workerKeyToActiveWorker.keys.filter(x => workerKeyToActiveWorker(x).equals(actorRef)).toSeq
      killedKeys.foreach(x => {
        val batchNr: Int = this.batchNrFromKey(x)
        log.debug(s"sending WorkerKilled message for batchNr: $batchNr to JobManager")
        workerKeyToJobManager.get(x).foreach(y => {
          y ! WorkerKilled(batchNr)
          workerKeyToJobManager -= x
        })
        workerKeyToActiveWorker -= x
      })
    case GetWorkerStatus(executionType, jobId, batchNr) =>
      val reportTo = sender()
      val key = workerKey(executionType, jobId, batchNr)
      val worker: Option[ActorRef] = workerKeyToActiveWorker.get(key)
      worker.foreach(x => x.tell(ReportResults, reportTo))
    case e =>
      log.warning(s"Unknown and unhandled message: '$e'")
  }

  def distributeRunnable(runnable: ActorRunnable[_, _, _, _], writerOpt: Option[Writers.Writer[MetricAggregation[Tag], Tag, _]]): Unit = {
    log.debug("received runnable for execution")
    val reportTo = sender()
    val runnableActor: ActorRef = context.actorOf(RunnableExecutionActor.probs(runnable.maxExecutionDuration, writerOpt))
    context.watch(runnableActor)
    val jobKey: String = workerKey(RUNNABLE, runnable.jobId, runnable.batchNr)
    workerKeyToJobManager.put(jobKey, reportTo)
    workerKeyToActiveWorker.put(jobKey, runnableActor)
    runnableActor.tell(runnable, reportTo)
    reportTo ! ACK(runnable.jobId, runnable.batchNr, self)
  }


}
