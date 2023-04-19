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

package de.awagen.kolibri.definitions.usecase.searchopt.domain

import de.awagen.kolibri.definitions.domain.TaskDataKeys
import de.awagen.kolibri.definitions.domain.TaskDataKeys.{TaskDataKeys, Val}
import de.awagen.kolibri.definitions.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.definitions.usecase.searchopt.provider.JudgementProvider


object ExtTaskDataKeys extends Enumeration {
  type ExtTaskDataKeys = TaskDataKeys


  val PRODUCT_ID_RESULT: TaskDataKeys.Val[Seq[String]] = Val[Seq[String]]("product_ids")
  val PRODUCT_ID_RESULT_FAILED: TaskDataKeys.Val[TaskFailType] = Val[TaskFailType]("product_id parsing failed")
  val JUDGEMENTS: TaskDataKeys.Val[Seq[Option[Double]]] = Val[Seq[Option[Double]]]("judgements")
  val JUDGEMENTS_FAILED: TaskDataKeys.Val[TaskFailType] = Val[TaskFailType]("judgements generation failed")
  val JUDGEMENT_PROVIDER: TaskDataKeys.Val[JudgementProvider[Double]] = Val[JudgementProvider[Double]]("judgement_provider")
  val JUDGEMENT_PROVIDER_FAILED: TaskDataKeys.Val[TaskFailType] = Val[TaskFailType]("judgement_provider_failed")
}
