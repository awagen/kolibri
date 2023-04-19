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

package de.awagen.kolibri.fleet.akka.actors.clusterinfo

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.ClusterEvent._
import akka.cluster.metrics.StandardMetrics.{Cpu, HeapMemory}
import akka.cluster.metrics._
import akka.cluster.{Cluster, MemberStatus}
import de.awagen.kolibri.fleet.akka.actors.clusterinfo.ClusterMetricsListenerActor.{LogMetrics, MetricsProvided, ProvideMetrics}
import de.awagen.kolibri.definitions.status.ClusterStates.{CapacityInfo, ClusterStatus, CpuInfo, HeapInfo}
import de.awagen.kolibri.datatypes.io.KolibriSerializable


object ClusterMetricsListenerActor {

  def props: Props = Props[ClusterMetricsListenerActor]

  case object LogMetrics extends KolibriSerializable

  case object ProvideMetrics extends KolibriSerializable

  case class MetricsProvided(stats: Seq[ClusterStatus]) extends KolibriSerializable

}


class ClusterMetricsListenerActor extends Actor with ActorLogging {

  val cluster: Cluster = Cluster(context.system)
  var nodes = Set.empty[Address]
  var nodeCpu: Map[Address, Cpu] = Map[Address, Cpu]()
  var nodeHeap: Map[Address, HeapMemory] = Map[Address, HeapMemory]()
  var cpuCapacity: Map[Address, Double] = Map[Address, Double]()
  var heapCapacity: Map[Address, Double] = Map[Address, Double]()
  var loadCapacity: Map[Address, Double] = Map[Address, Double]()
  var mixCapacity: Map[Address, Double] = Map[Address, Double]()

  override def preStart(): Unit = {
    //subscribing only to this ensures ClusterMetricsChanged events are received
    ClusterMetricsExtension(context.system).subscribe(self)

    //since we set nodes by the member* or CurrentClusterState Events, we also need to subscribe to akka.cluster.ClusterEvent.ClusterDomainEvent
    cluster.subscribe(self, initialStateMode = InitialStateAsSnapshot, classOf[akka.cluster.ClusterEvent.ClusterDomainEvent])
  }

  override def postStop(): Unit = {
    ClusterMetricsExtension(context.system).unsubscribe(self)
    cluster.unsubscribe(self)
  }

  override def receive: Receive = {
    case ClusterMetricsChanged(clusterMetrics) =>
      log.debug("ClusterMetricsChanged event received")
      clusterMetrics.foreach { nodeMetrics =>
        val address = nodeMetrics.address
        extractHeap(nodeMetrics).foreach(
          heap => nodeHeap = nodeHeap + (address -> heap))
        extractCpu(nodeMetrics).foreach(
          cpu => nodeCpu = nodeCpu + (address -> cpu))
      }
      extractCapacity(clusterMetrics)
    case state: CurrentClusterState =>
      log.debug("CurrentClusterState event received: {}", state)
      nodes = state.members.collect {
        case m if m.status == MemberStatus.Up => m.address
      }
      filterMetrics()
    case MemberUp(member) =>
      log.debug("Member is Up: {}", member.address)
      nodes += member.address
    case UnreachableMember(member) =>
      log.debug("Member detected as unreachable: {}", member)
    case ReachableMember(member) =>
      log.debug("Member detected as reachable after being unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.debug("Member is Removed: {} after {}",
        member.address, previousStatus)
      nodes -= member.address
      filterMetrics()
    case LogMetrics =>
      logMetrics()
    case ProvideMetrics =>
      sender() ! MetricsProvided(clusterStatus())

    case _: MemberEvent => //ignore
  }

  def filterMetrics(): Unit = {
    nodeHeap = nodeHeap.view.filterKeys(address => nodes.contains(address)).toMap
    nodeCpu = nodeCpu.view.filterKeys(address => nodes.contains(address)).toMap
  }

  def extractHeap(nodeMetrics: NodeMetrics): Option[HeapMemory] = {
    nodeMetrics match {
      case HeapMemory(address, timestamp, used, commited, max) =>
        Some(HeapMemory(address, timestamp, used, commited, max))
      case _ =>
        None
    }
  }

  def extractCpu(nodeMetrics: NodeMetrics): Option[Cpu] = nodeMetrics match {
    case Cpu(address, timestamp, systemLoadAverage, cpuCombined, cpuStolen, processors) =>
      Some(Cpu(address, timestamp, systemLoadAverage, cpuCombined, cpuStolen, processors))
    case _ =>
      None
  }

  def extractCapacity(nodeMetricsSet: Set[NodeMetrics]): Unit = {
    loadCapacity = SystemLoadAverageMetricsSelector.capacity(nodeMetricsSet)
    heapCapacity = HeapMetricsSelector.capacity(nodeMetricsSet)
    cpuCapacity = CpuMetricsSelector.capacity(nodeMetricsSet)
    mixCapacity = MixMetricsSelector.capacity(nodeMetricsSet)
  }

  def clusterStatus(): Seq[ClusterStatus] = {

    var nodeStats = Seq.empty[ClusterStatus]

    nodes.foreach { node =>
      val heap: Option[HeapMemory] = if (nodeHeap.keySet.contains(node)) Some(nodeHeap(node)) else None
      val cpu: Option[Cpu] = if (nodeCpu.keySet.contains(node)) Some(nodeCpu(node)) else None
      val loadCap: Option[Double] = if (loadCapacity.contains(node)) Some(loadCapacity(node)) else None
      val heapCap: Option[Double] = if (heapCapacity.contains(node)) Some(heapCapacity(node)) else None
      val cpuCap: Option[Double] = if (cpuCapacity.contains(node)) Some(cpuCapacity(node)) else None
      val mixCap: Option[Double] = if (mixCapacity.contains(node)) Some(mixCapacity(node)) else None

      //heap
      val host: String = node.host.fold[String]("")(identity)
      val port: Int = node.port.fold[Int](-1)(identity)
      val heapUsedVal: Long = heap.fold[Long](-1L)(x => x.used)
      val heapCommitedVal: Long = heap.fold[Long](-1L)(x => x.committed)
      val heapMaxVal: Long = heap.fold[Long](-1L)(x => x.max.fold[Long](-1L)(identity))
      val heapInfo: HeapInfo = HeapInfo(heapUsedVal, heapCommitedVal, heapMaxVal)

      //cpu
      val cpuCombinedVal: Double = cpu.fold[Double](-1D)(x => x.cpuCombined.fold[Double](-1D)(identity))
      val cpuStolenVal: Double = cpu.fold[Double](-1D)(x => x.cpuStolen.fold[Double](-1D)(identity))
      val loadAvgVal: Double = cpu.fold[Double](-1D)(x => x.systemLoadAverage.fold[Double](-1D)(identity))
      val nrProcessors: Int = cpu.fold[Int](-1)(x => x.processors)
      val cpuInfo: CpuInfo = CpuInfo(cpuCombinedVal, cpuStolenVal, loadAvgVal, nrProcessors)

      //capacities
      val loadCapVal = loadCap.fold[Double](-1D)(identity)
      val heapCapVal = heapCap.fold[Double](-1D)(identity)
      val cpuCapVal = cpuCap.fold[Double](-1D)(identity)
      val mixCapVal = mixCap.fold[Double](-1D)(identity)
      val capacityInfo: CapacityInfo = CapacityInfo(loadCapVal, heapCapVal, cpuCapVal, mixCapVal)

      nodeStats = nodeStats :+ ClusterStatus(host, port, heapInfo, cpuInfo, capacityInfo)
    }

    nodeStats
  }

  def logMetrics(): Unit = {
    println(s"### CLUSTER METRICS ${self.path.name} ###")
    import de.awagen.kolibri.definitions.status.ClusterStates.ClusterStatusImplicits.clusterStatusFormat
    import spray.json.DefaultJsonProtocol._
    import spray.json._

    val clusterStatusJson: JsValue = clusterStatus().toJson
    log.info(s"$clusterStatusJson")
  }

}
