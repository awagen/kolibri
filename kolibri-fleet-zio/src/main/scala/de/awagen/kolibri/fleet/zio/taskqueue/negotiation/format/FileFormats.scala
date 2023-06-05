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


package de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format

import de.awagen.kolibri.datatypes.mutable.stores.{TypeTaggedMap, TypedMapStore, WeaklyTypedMap}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.FileNameFormat
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.format.NameFormats.Parts.{BATCH_NR, CREATION_TIME_IN_MILLIS, JOB_ID, NODE_HASH, TOPIC}
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.persistence.reader.ClaimReader.ClaimTopic
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ClaimStates.Claim
import de.awagen.kolibri.fleet.zio.taskqueue.negotiation.state.ProcessId

import scala.collection.mutable

object FileFormats {

  case object JobDirectoryNameFormat extends FileNameFormat(Seq(JOB_ID, CREATION_TIME_IN_MILLIS), "_") {
    def getFileName(batchNr: Int, creationTimeInMillis: Long): String = {
      this.format(TypedMapStore(mutable.Map(
        BATCH_NR.namedClassTyped -> batchNr,
        CREATION_TIME_IN_MILLIS.namedClassTyped -> creationTimeInMillis,
      )))
    }
  }

  case object OpenTaskFileNameFormat extends FileNameFormat(Seq(BATCH_NR), "__") {
    def getFileName(batchNr: Int): String = {
      this.format(TypedMapStore(mutable.Map(BATCH_NR.namedClassTyped -> batchNr)))
    }
  }

  case object InProgressTaskFileNameFormat extends FileNameFormat(Seq(BATCH_NR), "__") {
    def getFileName(batchNr: Int): String = {
      this.format(TypedMapStore(mutable.Map(
        BATCH_NR.namedClassTyped -> batchNr
      )))
    }

    def processIdFromIdentifier(jobId: String, identifier: String): ProcessId = {
      val values: WeaklyTypedMap[String] = InProgressTaskFileNameFormat.parse(identifier.split("/").last)
      val batchNr: Int = values.get(BATCH_NR.namedClassTyped.name).get
      ProcessId(jobId, batchNr)
    }
  }

  case object ClaimFileNameFormat extends FileNameFormat(Seq(TOPIC, JOB_ID, BATCH_NR, CREATION_TIME_IN_MILLIS, NODE_HASH), "__") {

    def getFileName(claim: Claim): String = {
      val map: TypeTaggedMap = TypedMapStore(mutable.Map(
        TOPIC.namedClassTyped -> claim.claimTopic.toString,
        JOB_ID.namedClassTyped -> claim.jobId,
        BATCH_NR.namedClassTyped -> claim.batchNr,
        CREATION_TIME_IN_MILLIS.namedClassTyped -> claim.timeClaimedInMillis,
        NODE_HASH.namedClassTyped -> claim.nodeId
      ))
      format(map)
    }

    def claimFromIdentifier(identifier: String): Claim = {
      val fields = ClaimFileNameFormat.parse(identifier.split("/").last)
      Claim(
        fields.get[String](JOB_ID.namedClassTyped.name).get,
        fields.get[Int](BATCH_NR.namedClassTyped.name).get,
        fields.get[String](NODE_HASH.namedClassTyped.name).get,
        fields.get[Long](CREATION_TIME_IN_MILLIS.namedClassTyped.name).get,
        fields.get[String](TOPIC.namedClassTyped.name).map(x => ClaimTopic.withName(x)).get
      )
    }
  }

}
