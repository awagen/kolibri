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

import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.mutable.stores.WeaklyTypedMap


/**
 * Task representation. Defines the prerequisites that need to be available
 * locally in the passed around TypeTaggedMap,
 * the successKey as key under which result of the execution is stored,
 * fail key under which TaskFailType is stored in case execution fails.
 * task provides the actual execution, which in any case returns a TypeTaggedMap that in case of success
 * has result under successKey, or in case of failure has fail reason under failKey.
 */
trait ZIOTask[+T] extends KolibriSerializable {

  def prerequisiteKeys: Seq[String]

  def getMissingPrerequisiteKeys(map: WeaklyTypedMap[String]): Seq[String] = prerequisiteKeys
    .filter(x => !map.keySet.contains(x))

  def successKey: String

  def failKey: String

  def task(map: WeaklyTypedMap[String]): zio.Task[WeaklyTypedMap[String]]



}
