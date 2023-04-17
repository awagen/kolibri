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

package de.awagen.kolibri.fleet.akka.actors.resources

import de.awagen.kolibri.fleet.akka.cluster.ClusterStates.ClusterStatus

class CPUBasedResourceChecker extends ResourceChecker {

  override def freeSlots(states: Seq[ClusterStatus]): Int = {
    val nrOfNodes = states.size
    if (nrOfNodes == 0) return 0
    val overallLoad = states.map(x => x.cpuInfo.loadAvg).sum / nrOfNodes
    overallLoad match {
      case e if e < 0.5 => nrOfNodes * 5
      case e if e < 0.7 => nrOfNodes * 2
      case _ => 0
    }
  }
}
