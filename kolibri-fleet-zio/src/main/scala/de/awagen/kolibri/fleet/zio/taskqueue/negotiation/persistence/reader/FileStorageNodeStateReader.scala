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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader

import de.awagen.kolibri.fleet.zio.config.Directories
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.NodeUtilizationStates.NodeUtilizationStatesImplicits.nodeUtilizationStateFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.NodeUtilizationStates._
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import zio.{Task, ZIO}
import zio.stream.ZStream
import spray.json._

case class FileStorageNodeStateReader(reader: Reader[String, Seq[String]],
                                      overviewReader: DataOverviewReader) extends NodeStateReader {

  override def readNodeStates: Task[Seq[NodeUtilizationState]] = {
    for {
      stateFiles <- ZStream.fromIterable(overviewReader.listResources(Directories.NodeStates.nodeStateBaseFolder, _ => true))
        .runCollect
      _ <- ZIO.logDebug(s"found state files: $stateFiles")
      states <- ZStream.fromIterable(stateFiles)
        .map(file => s"${Directories.NodeStates.nodeStateBaseFolder.stripSuffix("/")}/${file.split("/").last}")
        .mapZIO(file => ZIO.attempt(reader.read(file).mkString("\n").parseJson.convertTo[NodeUtilizationState]))
        .either.filter({ case Right(_) => true; case _ => false; })
        .map(x => x.toOption.get)
        .runCollect
    } yield states
  }
}
