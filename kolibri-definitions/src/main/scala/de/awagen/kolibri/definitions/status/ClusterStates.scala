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


package de.awagen.kolibri.definitions.status

object ClusterStates {

  case class HeapInfo(heapUsed: Long,
                      heapCommited: Long,
                      heapMax: Long)

  case class CpuInfo(cpuCombined: Double,
                     cpuStolen: Double,
                     loadAvg: Double,
                     nrProcessors: Int)

  case class CapacityInfo(loadCapacity: Double,
                          heapCapacity: Double,
                          cpuCapacity: Double,
                          mixCapacity: Double)


  case class ClusterStatus(host: String,
                           port: Int,
                           heapInfo: HeapInfo,
                           cpuInfo: CpuInfo,
                           capacityInfo: CapacityInfo)


  object ClusterStatusImplicits {

    import spray.json.DefaultJsonProtocol._
    import spray.json.RootJsonFormat

    implicit val heapInfoFormat: RootJsonFormat[HeapInfo] = jsonFormat3(HeapInfo)
    implicit val cpuInfoFormat: RootJsonFormat[CpuInfo] = jsonFormat4(CpuInfo)
    implicit val capInfoFormat: RootJsonFormat[CapacityInfo] = jsonFormat4(CapacityInfo)
    implicit val clusterStatusFormat: RootJsonFormat[ClusterStatus] = jsonFormat5(ClusterStatus)
  }

}
