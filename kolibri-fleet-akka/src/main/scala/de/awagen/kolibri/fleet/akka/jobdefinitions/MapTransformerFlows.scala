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


package de.awagen.kolibri.fleet.akka.jobdefinitions

import akka.NotUsed
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.definitions.processing.ProcessingMessages.{Corn, ProcessingMessage}
import de.awagen.kolibri.datatypes.tagging.MapImplicits.MutableTaggedMap

object MapTransformerFlows extends Enumeration {
  type MapTransformerFlows = Val

  case class Val(transformerFlow: Flow[MutableTaggedMap[String, Seq[Any]], ProcessingMessage[Any], NotUsed]) extends super.Val

  val IDENTITY: Val = Val(Flow.fromFunction(x => Corn(x)))


}
