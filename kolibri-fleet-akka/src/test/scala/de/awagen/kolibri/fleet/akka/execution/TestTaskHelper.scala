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

package de.awagen.kolibri.fleet.akka.execution

import de.awagen.kolibri.base.domain.TaskDataKeys.Val
import de.awagen.kolibri.base.processing.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.base.processing.execution
import de.awagen.kolibri.base.processing.execution.task.SimpleSyncTask
import de.awagen.kolibri.base.processing.failure.TaskFailType.{FailedByException, TaskFailType}
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.fleet.akka.execution.task.SimpleAsyncTask

import scala.concurrent.{ExecutionContext, Future}


object TestTaskHelper {

  val productIdResult: Val[Seq[String]] = Val("product_ids")
  val concatIdKey: Val[String] = Val("concatenated_product_ids")
  val reversedIdKey: Val[String] = Val("reversed_product_ids")
  val reversedIdKeyPM: Val[ProcessingMessage[String]] = Val("reversed_product_ids")
  val reversedIdSeqKey: Val[Seq[String]] = Val("reversed_product_ids")
  val reversedIdSeqSublistKey: Val[Seq[String]] = Val("reversed_product_ids_sublist")
  val failTaskKey: Val[Unit] = Val[Unit]("fail_task")
  val taskFailTypeKey: Val[TaskFailType] = Val("task_fail_type")
  val eitherKey: Val[Either[Int, String]] = Val("either_task")

  val concatIdsFunc: TypeTaggedMap => Either[TaskFailType, String] = x => Right(x.get(productIdResult).map(y => y.mkString(",")).getOrElse(""))
  val reverseIdsFunc: TypeTaggedMap => Either[TaskFailType, String] = x => Right(x.get(concatIdKey).map(y => y.reverse).getOrElse(""))
  val reverseIdsFuncPM: TypeTaggedMap => Either[TaskFailType, ProcessingMessage[String]] = x => Right(x.get(concatIdKey).map(y => Corn(y.reverse)).getOrElse(Corn("")))
  val reverseIdsValueFunc: TypeTaggedMap => String = x => x.get(concatIdKey).map(y => y.reverse).getOrElse("")
  val reverseIdSeqFunc: TypeTaggedMap => Either[TaskFailType, Seq[String]] = x => Right(x.get(productIdResult).map(y => y.reverse).getOrElse(Seq.empty))
  val subListFromReverseProductIdsFunc: TypeTaggedMap => Either[TaskFailType, Seq[String]] = x => Right(
    x.get(reversedIdSeqKey).map(y => reversedIdSeqKey.castFunc.apply(y)).map(z => z.take(2)).getOrElse(Seq.empty))
  val failFunc: TypeTaggedMap => Either[TaskFailType, Unit] = _ =>
    Left(FailedByException(new RuntimeException("failed")))

  def concatIdsTask: SimpleSyncTask[String] = execution.task.SimpleSyncTask[String](Seq(productIdResult), concatIdKey, taskFailTypeKey, concatIdsFunc)

  def reverseIdsTask: SimpleSyncTask[String] = SimpleSyncTask[String](Seq(concatIdKey), reversedIdKey, taskFailTypeKey, reverseIdsFunc)

  def reverseIdsTaskPM: SimpleSyncTask[ProcessingMessage[String]] = SimpleSyncTask[ProcessingMessage[String]](Seq(concatIdKey), reversedIdKeyPM, taskFailTypeKey, reverseIdsFuncPM)

  def reverseIdSeqTask: SimpleSyncTask[Seq[String]] = execution.task
    .SimpleSyncTask[Seq[String]](Seq(productIdResult), reversedIdSeqKey, taskFailTypeKey, reverseIdSeqFunc)

  def reverseIdSeqSubseqTask: SimpleSyncTask[Seq[String]] = execution.task
    .SimpleSyncTask[Seq[String]](Seq(reversedIdSeqKey), reversedIdSeqSublistKey, taskFailTypeKey, subListFromReverseProductIdsFunc)

  def failTask: SimpleSyncTask[Unit] = execution.task.SimpleSyncTask[Unit](Seq.empty, failTaskKey, taskFailTypeKey, failFunc)

  def asyncReverseIdsTask(implicit ec: ExecutionContext): SimpleAsyncTask[ProcessingMessage[String], ProcessingMessage[String]] = SimpleAsyncTask(
    Seq(concatIdKey),
    reversedIdKeyPM,
    taskFailTypeKey,
    x => Future {
      Corn(reverseIdsValueFunc.apply(x))
    }, (_, _) => (), _ => ())


}
