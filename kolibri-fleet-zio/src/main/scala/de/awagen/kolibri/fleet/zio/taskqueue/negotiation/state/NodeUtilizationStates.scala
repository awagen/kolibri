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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state


object NodeUtilizationStates {

  case class CpuInfo(numCores: Int,
                     cpuLoad: Double)

  case class MemoryInfo(used: Double,
                        committed: Double,
                        max: Double)

  case class NodeUtilizationState(lastUpdate: String,
                                  nodeId: String,
                                  cpuInfo: CpuInfo,
                                  heapMemoryInfo: MemoryInfo,
                                  nonHeapMemoryInfo: MemoryInfo)

  object NodeUtilizationStatesImplicits {

    import spray.json.DefaultJsonProtocol._
    import spray.json.RootJsonFormat

    implicit val heapInfoFormat: RootJsonFormat[MemoryInfo] = jsonFormat3(MemoryInfo)
    implicit val cpuInfoFormat: RootJsonFormat[CpuInfo] = jsonFormat2(CpuInfo)
    implicit val nodeUtilizationStateFormat: RootJsonFormat[NodeUtilizationState] = jsonFormat5(NodeUtilizationState)

  }


}
