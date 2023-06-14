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


package de.awagen.kolibri.fleet.zio.config.di

import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.types.Types.WithCount
import de.awagen.kolibri.datatypes.values.DataPoint
import de.awagen.kolibri.datatypes.values.aggregation.immutable.Aggregators
import de.awagen.kolibri.definitions.directives.Resource
import de.awagen.kolibri.fleet.zio.config.{AppConfig, AppProperties}
import de.awagen.kolibri.fleet.zio.execution.JobDefinitions
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.{ClaimReader, FileStorageClaimReader, FileStorageJobStateReader, FileStorageWorkStateReader, JobStateReader, WorkStateReader}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.writer.{ClaimWriter, FileStorageClaimWriter, FileStorageJobStateWriter, FileStorageWorkStateWriter, JobStateWriter, WorkStateWriter}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.services.{BaseClaimService, BaseWorkHandlerService, ClaimService, WorkHandlerService}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessId
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers._
import zio.{Fiber, Queue, Ref, ULayer, ZIO, ZLayer}

/**
 * Constructing the layers to provide to the ZIO application
 */
object ZioDIConfig {

  val writerLayer: ULayer[Writer[String, String, _]] = ZLayer.succeed(AppConfig.persistenceModule.persistenceDIModule.writer)

  val readerLayer: ULayer[Reader[String, Seq[String]]] = ZLayer.succeed(AppConfig.persistenceModule.persistenceDIModule.reader)

  val overviewReaderLayer: ULayer[DataOverviewReader] = ZLayer.succeed(AppConfig.persistenceModule.persistenceDIModule.dataOverviewReaderUnfiltered)

  val jobStateReaderLayer: ZLayer[DataOverviewReader with Reader[String, Seq[String]], Nothing, JobStateReader] = ZLayer {
    for {
      overviewReader <- ZIO.service[DataOverviewReader]
      fileReader <- ZIO.service[Reader[String, Seq[String]]]
    } yield FileStorageJobStateReader(overviewReader, fileReader)
  }

  val jobStateWriterLayer: ZLayer[Writer[String, String, Unit], Nothing, JobStateWriter] = ZLayer {
    for {
      writer <- ZIO.service[Writer[String, String, Unit]]
    } yield FileStorageJobStateWriter(writer)
  }

  val claimReaderLayer: ZLayer[((String => Boolean) => DataOverviewReader) with Reader[String, Seq[String]], Nothing, ClaimReader] = ZLayer {
    for {
      overviewReaderFunc <- ZIO.service[(String => Boolean) => DataOverviewReader]
      reader <- ZIO.service[Reader[String, Seq[String]]]
    } yield FileStorageClaimReader(overviewReaderFunc, reader)
  }

  val claimWriterLayer: ZLayer[FileWriter[String, Unit], Nothing, ClaimWriter] = ZLayer {
    for {
      writer <- ZIO.service[FileWriter[String, Unit]]
    } yield FileStorageClaimWriter(writer)
  }

  val workStateReaderLayer: ZLayer[((String => Boolean) => DataOverviewReader) with Reader[String, Seq[String]], Nothing, WorkStateReader] = ZLayer {
    for {
      overviewReaderFunc <- ZIO.service[(String => Boolean) => DataOverviewReader]
      reader <- ZIO.service[Reader[String, Seq[String]]]
    } yield FileStorageWorkStateReader(overviewReaderFunc, reader)
  }

  val workStateWriterLayer: ZLayer[Writer[String, String, Unit], Nothing, WorkStateWriter] = ZLayer {
    for {
      writer <- ZIO.service[Writer[String, String, Unit]]
    } yield FileStorageWorkStateWriter(writer)
  }

  val claimServiceLayer: ZLayer[ClaimReader with ClaimWriter with WorkStateReader with WorkStateWriter with JobStateWriter, Nothing, ClaimService] = ZLayer {
    for {
      claimReader <- ZIO.service[ClaimReader]
      claimWriter <- ZIO.service[ClaimWriter]
      workStateReader <- ZIO.service[WorkStateReader]
      workStateWriter <- ZIO.service[WorkStateWriter]
      jobStateWriter <- ZIO.service[JobStateWriter]
    } yield BaseClaimService(claimReader, claimWriter, workStateReader, workStateWriter, jobStateWriter)
  }

  val workHandlerServiceLayer: ZLayer[ClaimReader with WorkStateReader with WorkStateWriter, Nothing, WorkHandlerService] = ZLayer {
    for {
      claimReader <- ZIO.service[ClaimReader]
      workStateReader <- ZIO.service[WorkStateReader]
      workStateWriter <- ZIO.service[WorkStateWriter]
      queue <- Queue.bounded[JobDefinitions.JobBatch[_, _, _ <: WithCount]](AppProperties.config.maxNrJobsClaimed)
      addedBatchesHistory <- Ref.make(Seq.empty[ProcessId])
      processIdToAggregatorMappingRef <- Ref.make(Map.empty[ProcessId, Ref[Aggregators.Aggregator[_ <: TaggedWithType with DataPoint[Any], _ <: WithCount]]])
      processIdToFiberMappingRef <- Ref.make(Map.empty[ProcessId, Fiber.Runtime[Throwable, Unit]])
      resourceToJobIdMappingRef <- Ref.make(Map.empty[Resource[Any], Set[String]])
    } yield BaseWorkHandlerService(
      claimReader,
      workStateReader,
      workStateWriter,
      queue,
      addedBatchesHistory,
      processIdToAggregatorMappingRef,
      processIdToFiberMappingRef,
      resourceToJobIdMappingRef
    )
  }


}
