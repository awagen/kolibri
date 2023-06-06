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


package de.awagen.kolibri.fleet.zio.testutils

import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.BatchAggregationInfo
import de.awagen.kolibri.fleet.zio.execution.ZIOTasks.SimpleWaitTask
import de.awagen.kolibri.fleet.zio.execution.aggregation.Aggregators.countingAggregator
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{FileStorageClaimReader, FileStorageJobStateReader, FileStorageWorkStateReader, JobStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.{FileStorageClaimWriter, FileStorageJobStateWriter, JobStateWriter}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.{BaseClaimService, BaseWorkHandlerService, WorkHandlerService}
import de.awagen.kolibri.storage.io.reader.{LocalDirectoryReader, LocalResourceFileReader}
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.FileStorageWorkStateWriter
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.{JobStateSnapshot, OpenJobsSnapshot}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessId
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{doNothing, doReturn}
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.{Fiber, Queue, Ref, Task, ZIO}

object TestObjects {

  val baseResourceFolder: String = getClass.getResource("/testdata").getPath

  def fileWriterMock: FileWriter[String, Unit] = {
    val mocked = mock[FileWriter[String, Unit]]
    doNothing().when(mocked).moveDirectory(ArgumentMatchers.any[String], ArgumentMatchers.any[String])
    doReturn(Right(())).when(mocked).write(ArgumentMatchers.any[String], ArgumentMatchers.any[String])
    doNothing().when(mocked).copyDirectory(ArgumentMatchers.any[String], ArgumentMatchers.any[String])
    doReturn(Right()).when(mocked).delete(ArgumentMatchers.any[String])
    mocked
  }

  def jobStateReader(baseFolder: String): JobStateReader = FileStorageJobStateReader(
    LocalDirectoryReader(
      baseDir = baseFolder,
      baseFilenameFilter = _ => true),
    LocalResourceFileReader(
      basePath = baseFolder,
      delimiterAndPosition = None,
      fromClassPath = false
    )
  )

  def jobStateWriter(writer: FileWriter[String, Unit]): JobStateWriter = FileStorageJobStateWriter(
    writer
  )

  def claimReader: FileStorageClaimReader = FileStorageClaimReader(
    filter => LocalDirectoryReader(baseDir = baseResourceFolder, baseFilenameFilter = filter),
    LocalResourceFileReader(
      basePath = baseResourceFolder,
      delimiterAndPosition = None,
      fromClassPath = false
    )
  )

  def claimWriter(writer: FileWriter[String, Unit]) = FileStorageClaimWriter(writer)

  def workStateReader = FileStorageWorkStateReader(
    filter => LocalDirectoryReader(baseDir = baseResourceFolder, baseFilenameFilter = filter),
    LocalResourceFileReader(
      basePath = baseResourceFolder,
      delimiterAndPosition = None,
      fromClassPath = false
    )
  )

  def workStateWriter(writer: FileWriter[String, Unit]) = FileStorageWorkStateWriter(
    writer
  )

  def claimService(writer: FileWriter[String, Unit]) = BaseClaimService(
    claimReader,
    claimWriter(writer),
    workStateReader
  )

  def workHandler[U <: TaggedWithType with DataPoint[Any], V <: WithCount](writer: FileWriter[String, Unit],
                                                                           jobBatchQueueSize: Int = 5,
                                                                           addedBatchesHistoryInitState: Seq[ProcessId] = Seq.empty[ProcessId],
                                                                           processIdToAggregatorMappingInitState: Map[ProcessId, Ref[Aggregators.Aggregator[U, V]]] = Map.empty[ProcessId, Ref[Aggregators.Aggregator[U, V]]],
                                                                           processIdToFiberMappingInitState: Map[ProcessId, Fiber.Runtime[Throwable, Unit]] = Map.empty[ProcessId, Fiber.Runtime[Throwable, Unit]]
                                                                          ): Task[WorkHandlerService] = for {
    queue <- Queue.bounded[JobDefinitions.JobBatch[_, _, _ <: WithCount]](jobBatchQueueSize)
    addedBatchesHistory <- Ref.make(addedBatchesHistoryInitState)
    processIdToAggregatorMappingRef <- Ref.make(processIdToAggregatorMappingInitState)
    processIdToFiberMappingRef <- Ref.make(processIdToFiberMappingInitState)
    workHandler <- ZIO.attempt({
      BaseWorkHandlerService(
        workStateReader,
        workStateWriter(writer),
        queue,
        addedBatchesHistory,
        processIdToAggregatorMappingRef,
        processIdToFiberMappingRef
      )
    })
  } yield workHandler

  object SnapshotSample1 {

    def batchAggregationInfo: BatchAggregationInfo[Unit, JobDefinitions.ValueWithCount[Int]] = BatchAggregationInfo(
      Left(SimpleWaitTask.successKey),
      () => countingAggregator(0, 0)
    )

    def jobDef: JobDefinitions.JobDefinition[Int, Unit, JobDefinitions.ValueWithCount[Int]] = {
      JobDefinitions.simpleWaitJob(
        "testJob1_3434839787",
        1,
        1000L,
        1,
        batchAggregationInfo
      )
    }

    def openJobsSnapshot = {
      OpenJobsSnapshot(
        Map("testJob1_3434839787" -> JobStateSnapshot(
          "testJob1_3434839787",
          3434839787L,
          jobDef,
          Set(JobDirectives.Process),
          Map(1 -> BatchProcessingStates.Open)
        ))
      )
    }

  }

}
