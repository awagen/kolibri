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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer

import de.awagen.kolibri.fleet.zio.config.{AppProperties, Directories}
import de.awagen.kolibri.fleet.zio.metrics.Metrics
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.FileFormats.NodeHealthFileNameFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.NodeUtilizationStates.NodeUtilizationStatesImplicits.nodeUtilizationStateFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.NodeUtilizationStates._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessingStateUtils
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import spray.json.enrichAny
import zio.{Task, ZIO}


case class FileStorageNodeStateWriter(writer: Writer[String, String, _]) extends NodeStateWriter {

  private[writer] def getNodeStatus: Task[NodeUtilizationState] = {
    for {
      avgLoad <- Metrics.CalculationsWithMetrics.avgSystemLoad
      heapUsed <- Metrics.CalculationsWithMetrics.getUsedHeapMemory
      nonHeapUsed <- Metrics.CalculationsWithMetrics.getUsedNonHeapMemory
      heapCommitted <- Metrics.CalculationsWithMetrics.getCommittedHeapMemory
      nonHeapCommitted <- Metrics.CalculationsWithMetrics.getCommittedNonHeapMemory
      heapMax <- Metrics.CalculationsWithMetrics.getMaxHeapMemory
      nonHeapMax <- Metrics.CalculationsWithMetrics.getMaxNonHeapMemory
      numProcessors <- Metrics.CalculationsWithMetrics.getAvailableProcessors
    } yield NodeUtilizationState(
      ProcessingStateUtils.timeInMillisToFormattedTime(System.currentTimeMillis()),
      AppProperties.config.node_hash,
      CpuInfo(numProcessors, avgLoad),
      MemoryInfo(heapUsed, heapCommitted, heapMax),
      MemoryInfo(nonHeapUsed, nonHeapCommitted, nonHeapMax)
    )
  }

  override def persistStatusUpdate: Task[Unit] = {
    for {
      nodeStateBaseFolder <- ZIO.succeed(Directories.NodeStates.nodeStateBaseFolder)
      fileName <- ZIO.attempt(NodeHealthFileNameFormat.getFileName(AppProperties.config.node_hash))
      nodeStatus <- getNodeStatus
      _ <- ZIO.attemptBlockingIO(writer.write(nodeStatus.toJson.toString(), s"${nodeStateBaseFolder.stripSuffix("/")}/$fileName"))
    } yield ()
  }

  override def removeNodeState(nodeHash: String): Task[Unit] = {
    ZIO.fromEither(writer.delete(Directories.NodeStates.nodeStateFile(nodeHash)))
      .map(_ => ())
  }
}
