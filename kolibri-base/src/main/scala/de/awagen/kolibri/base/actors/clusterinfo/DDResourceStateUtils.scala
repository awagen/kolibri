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


package de.awagen.kolibri.base.actors.clusterinfo

import akka.actor.ActorRef
import akka.cluster.ddata.Replicator.Update
import akka.cluster.ddata._
import akka.cluster.ddata.typed.scaladsl.Replicator.WriteLocal

object DDResourceStateUtils {

  // identifier and key for the central actor reference to retrieve the batch updates
  val DD_BATCH_INFO_ACTOR_REF_ID: String = "ddBatchInfoActor"
  val DD_BATCH_STATUS_ACTOR_REF_KEY: Key[ORSet[ActorRef]] = ORSetKey.create(DD_BATCH_INFO_ACTOR_REF_ID)
  // identifier and key for mapping of judgement identifiers to jobs utilizing them
  val DD_JUDGEMENT_RESOURCE_USAGE_JOB_MAPPING_ID: String = "ddJudgementJobMapping"
  val DD_JUDGEMENT_JOB_MAPPING_KEY: Key[ORMap[String, ORSet[String]]] = ORMapKey.create[String, ORSet[String]](DD_JUDGEMENT_RESOURCE_USAGE_JOB_MAPPING_ID)


  /**
   * Initialize
   *
   * @return
   */
  def ddJudgementMappingInit(): Update[ORMap[String, ORSet[String]]] = {
    Update[ORMap[String, ORSet[String]]](DD_JUDGEMENT_JOB_MAPPING_KEY, ORMap.empty[String, ORSet[String]], WriteLocal)(identity)
  }

  /**
   * Adding jobId to the judgement mapping for the given judgementsResourceId key. The judgementsResourceId should
   * usually refer to the key used in the repository
   *
   * @param selfAddress          - self address of the node where this update is created
   * @param judgementsResourceId - the resource if for the judgement data
   * @param jobId                - the jobId to add to the set of job identifiers for the specified resource. Indicates which jobIds
   *                             are currently running and using this resource. The information of those mappings can be used per node
   *                             to clean up global resources (such as judgement repository) to avoid resources remaining in memory
   *                             when not needed anymore, while still avoiding too frequent cleanup
   * @return
   */
  def ddJudgementJobMappingUpdateAdd(selfAddress: SelfUniqueAddress, judgementsResourceId: String, jobId: String): Update[ORMap[String, ORSet[String]]] = {
    val addMap: ORMap[String, ORSet[String]] = ORMap.create()
      .put(selfAddress, judgementsResourceId, ORSet.create()
        .add(selfAddress, jobId)
      )
    Update[ORMap[String, ORSet[String]]](DD_JUDGEMENT_JOB_MAPPING_KEY, ORMap.empty[String, ORSet[String]], WriteLocal)(map => map.merge(addMap))
  }

  def ddJudgementJobMappingUpdateRemove(judgementsResourceId: String, jobId: String)(implicit selfAddress: SelfUniqueAddress): Update[ORMap[String, ORSet[String]]] = {
    Update[ORMap[String, ORSet[String]]](DD_JUDGEMENT_JOB_MAPPING_KEY, ORMap.empty[String, ORSet[String]], WriteLocal)(map => {
      // if the option is empty, we dont need to do anything
      // if contains value, update set with the passed jobId removed
      map.get(judgementsResourceId)
        .map(set => set.remove(jobId))
        .map(updatedSet => map.updated(selfAddress, judgementsResourceId, map.get(judgementsResourceId).getOrElse(ORSet.empty))(_ => updatedSet))
        .getOrElse(map)
    })
  }

}
