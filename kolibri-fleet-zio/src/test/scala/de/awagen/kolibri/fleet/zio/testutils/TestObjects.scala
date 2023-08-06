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
import de.awagen.kolibri.definitions.directives.Resource
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions.{BatchAggregationInfo, JobDefinition}
import de.awagen.kolibri.fleet.zio.execution.ZIOTasks.SimpleWaitTask
import de.awagen.kolibri.fleet.zio.execution.aggregation.Aggregators.countingAggregator
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.directives.JobDirectives
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.{BaseTaskOverviewService, BaseWorkHandlerService, ClaimBasedTaskPlannerService}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.JobStates.{JobStateSnapshot, OpenJobsSnapshot}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessingStatus.ProcessingStatus
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state._
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.status.BatchProcessingStates
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.utils.{DataTypeUtils, DateUtils}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, LocalDirectoryReader, LocalResourceFileReader}
import de.awagen.kolibri.storage.io.writer.Writers.{FileWriter, Writer}
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

  def reader(baseFolder: String) = LocalResourceFileReader(
    basePath = baseFolder,
    delimiterAndPosition = None,
    fromClassPath = false
  )

  def overviewReader(baseFolder: String): DataOverviewReader = {
    LocalDirectoryReader(
      baseDir = baseFolder,
      baseFilenameFilter = _ => true)
  }

  def jobStateReader(baseFolder: String): JobStateReader = FileStorageJobStateReader(
    overviewReader(baseFolder),
    reader(baseFolder)
  )

  def jobStateWriter(writer: FileWriter[String, Unit]): JobStateWriter = FileStorageJobStateWriter(
    writer,
    jobStateReader(baseResourceFolder)
  )

  def claimReader: FileStorageClaimReader = FileStorageClaimReader(
    filter => LocalDirectoryReader(baseDir = baseResourceFolder, baseFilenameFilter = filter),
    reader(baseResourceFolder)
  )

  def claimWriter(writer: FileWriter[String, Unit]) = FileStorageClaimWriter(writer)

  def workStateReader = FileStorageWorkStateReader(
    filter => LocalDirectoryReader(baseDir = baseResourceFolder, baseFilenameFilter = filter),
    reader(baseResourceFolder)
  )

  def workStateWriter(writer: Writer[String, String, Unit]) = FileStorageWorkStateWriter(
    reader(baseResourceFolder),
    writer
  )

  def nodeStateReader = FileStorageNodeStateReader(
    reader(baseResourceFolder),
    overviewReader(baseResourceFolder)
  )

  def nodeStateWriter(writer: Writer[String, String, Unit]) = FileStorageNodeStateWriter(
    writer
  )

  def taskOverviewService(jobStateReaderInst: JobStateReader = jobStateReader(baseResourceFolder)) = BaseTaskOverviewService(
    jobStateReaderInst,
    workStateReader,
    nodeStateReader
  )

  def claimService(writer: FileWriter[String, Unit], jobStateReaderInst: JobStateReader = jobStateReader(baseResourceFolder)) = ClaimBasedTaskPlannerService(
    claimReader,
    claimWriter(writer),
    workStateReader,
    workStateWriter(writer),
    jobStateReaderInst,
    jobStateWriter(writer),
    nodeStateWriter(writer)
  )

  def workHandler[U <: TaggedWithType with DataPoint[Any], V <: WithCount](writer: FileWriter[String, Unit],
                                                                           jobBatchQueueSize: Int = 5,
                                                                           initialQueueContent: Seq[JobDefinitions.JobBatch[_, _, _ <: WithCount]] = Seq.empty,
                                                                           addedBatchesHistoryInitState: Seq[ProcessId] = Seq.empty[ProcessId],
                                                                           processIdToAggregatorMappingInitState: Map[ProcessId, Ref[Aggregators.Aggregator[U, V]]] = Map.empty[ProcessId, Ref[Aggregators.Aggregator[U, V]]],
                                                                           processIdToFiberMappingInitState: Map[ProcessId, Fiber.Runtime[Throwable, Unit]] = Map.empty[ProcessId, Fiber.Runtime[Throwable, Unit]],
                                                                           resourceToJobIdMapping: Map[Resource[Any], Set[String]] = Map.empty[Resource[Any], Set[String]],
                                                                           jobIdToJobDefMapping: Map[String, JobDefinition[_, _, _ <: WithCount]] = Map.empty[String, JobDefinition[_, _, _ <: WithCount]],
                                                                          ): Task[BaseWorkHandlerService[_ <: TaggedWithType with DataPoint[Any], _ <: WithCount]] = for {
    queue <- Queue.bounded[JobDefinitions.JobBatch[_, _, _ <: WithCount]](jobBatchQueueSize)
    _ <- DataTypeUtils.addElementsToQueue(initialQueueContent, queue)
    addedBatchesHistory <- Ref.make(addedBatchesHistoryInitState)
    processIdToAggregatorMappingRef <- Ref.make(processIdToAggregatorMappingInitState)
    processIdToFiberMappingRef <- Ref.make(processIdToFiberMappingInitState)
    resourceToJobIdMappingRef <- Ref.make(resourceToJobIdMapping)
    jobIdToJobDefRef <- Ref.make(jobIdToJobDefMapping)
    workHandler <- ZIO.attempt({
      BaseWorkHandlerService(
        claimReader,
        workStateReader,
        workStateWriter(writer),
        queue,
        addedBatchesHistory,
        processIdToAggregatorMappingRef,
        processIdToFiberMappingRef,
        resourceToJobIdMappingRef,
        jobIdToJobDefRef
      )
    })
  } yield workHandler

  object SnapshotSample1 {

    def batchAggregationInfo: BatchAggregationInfo[Unit, JobDefinitions.ValueWithCount[Int]] = BatchAggregationInfo(
      SimpleWaitTask.successKey,
      () => countingAggregator(0, 0)
    )

    def jobDef: JobDefinitions.JobDefinition[Int, Unit, JobDefinitions.ValueWithCount[Int]] = {
      JobDefinitions.simpleWaitJob(
        "testJob1_3434839787",
        1,
        1L,
        100,
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
          Map(0 -> BatchProcessingStates.Open, 1 -> BatchProcessingStates.Open)
        ))
      )
    }

  }

  val processingState1 = ProcessingState(
    ProcessId(
      "testJob1_3434839787",
      0
    ),
    ProcessingInfo(
      ProcessingStatus.DONE,
      100,
      0,
      "abc234",
      DateUtils.timeInMillisToFormattedTime(1703845333850L)
    )
  )

  val processingState2 = ProcessingState(
    ProcessId(
      "testJob1_3434839787",
      1
    ),
    ProcessingInfo(
      ProcessingStatus.PLANNED,
      100,
      0,
      "abc234",
      DateUtils.timeInMillisToFormattedTime(1703845333850L)
    )
  )

  def copyProcessingStateWithAdjustedTimeAndState(state: ProcessingState, timeStr: String, processingStatus: ProcessingStatus): ProcessingState = {
    state.copy(processingInfo = state.processingInfo.copy(
      lastUpdate = timeStr,
      processingStatus = processingStatus
    ))
  }

}
