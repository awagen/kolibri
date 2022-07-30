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

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.ddata.Replicator.{Subscribe, Update}
import akka.cluster.ddata.typed.scaladsl.Replicator.WriteLocal
import akka.cluster.ddata.{Key, ORMap, ORSet}
import de.awagen.kolibri.base.actors.clusterinfo.LocalResourceManagerActor.{ExistingResourceIdToJobMapping, GetExistingResourceIdToJobMapping, RemoveValueFromAllMappings}
import de.awagen.kolibri.base.cluster.ClusterNode
import de.awagen.kolibri.base.usecase.searchopt.provider.FileBasedJudgementRepository
import de.awagen.kolibri.datatypes.io.KolibriSerializable

object LocalResourceManagerActor {

  def props: Props = Props[LocalResourceManagerActor]

  trait LocalResourceManagerMsg extends KolibriSerializable

  case object GetExistingResourceIdToJobMapping extends LocalResourceManagerMsg

  case class ExistingResourceIdToJobMapping(mapping: Map[String, Set[String]]) extends LocalResourceManagerMsg

  case class RemoveValueFromAllMappings(ddResourceKey: Key[ORMap[String, ORSet[String]]], jobId: String) extends LocalResourceManagerMsg


}

case class LocalResourceManagerActor() extends Actor with ActorLogging {

  var resourceIdToJobMapping: Map[String, Set[String]] = Map.empty

  // subscribe to receive replication messages for the mapping of used resource types to the jobs
  DDResourceStateUtils.DD_RESOURCETYPE_TO_KEY_MAPPING.values.foreach(value => {
    ClusterNode.getSystemSetup.ddReplicator ! Subscribe(value, self)
  })

  // set up partial function to handle all messages for the known keys
  def ddReceive: Receive = DistributedDataActorHelper.multipleStateChangeReceive[ORMap[String, ORSet[String]]](
    DDResourceStateUtils.DD_RESOURCETYPE_TO_KEY_MAPPING.values.map(value => (value, value.id)).toSeq,
    valueHandleFunc)

  val valueHandleFunc: ORMap[String, ORSet[String]] => Unit = map => {
    // pick the updated mapping
    val value: Map[String, Set[String]] = map.entries.map(x => (x._1, x._2.elements))
    log.debug(s"current value: $resourceIdToJobMapping, new value: $value")
    // check for each map root key whether Set is empty or missing now but existed before
    // and then make remove call for those keys to judgements provider
    resourceIdToJobMapping.keySet
      .filter(key => resourceIdToJobMapping(key).nonEmpty)
      .filter(key => value.getOrElse(key, Set.empty).isEmpty)
      .foreach(key => {
        log.info(s"Calling key remove on resource id for key '$key'")
        FileBasedJudgementRepository.remove(key)
      })
    // update the current value of mappings
    resourceIdToJobMapping = value
  }

  override def receive: Receive = ddReceive.orElse[Any, Unit] {
    case GetExistingResourceIdToJobMapping =>
      sender() ! ExistingResourceIdToJobMapping(resourceIdToJobMapping)
    case msg@RemoveValueFromAllMappings(resourceKey, jobId) =>
      // check in which mappings jobId occurs, and for each root key send remove update to
      // replicator
      log.info(s"received removal message: $msg")
      log.debug(s"current state: $resourceIdToJobMapping")
      resourceIdToJobMapping
        .filter(keyValue => keyValue._2.contains(jobId))
        .map(keyValue => {
          val newKeyValue = (keyValue._1, keyValue._2 - jobId)
          log.debug(s"modifying mapping '$keyValue' to '$newKeyValue'")
          newKeyValue
        })
        .foreach(newKeyValue => {
          log.debug(s"removing jobId '$jobId' from mapping for key '${newKeyValue._1}'")
          ClusterNode.getSystemSetup.ddReplicator !
            Update[ORMap[String, ORSet[String]]](resourceKey, ORMap.empty[String, ORSet[String]], WriteLocal)(map => {
              var newMap = map
              if (newKeyValue._2.isEmpty) {
                newMap = newMap.remove(newKeyValue._1)(ClusterNode.getSystemSetup.ddSelfUniqueAddress)
                log.debug(s"prep of new ORMap, current value: ${map.entries}, new value: ${newMap.entries}")
                newMap
              }
              else {
                map.updated(ClusterNode.getSystemSetup.ddSelfUniqueAddress, newKeyValue._1, ORSet.empty)(currentSetValue => {
                  log.debug(s"prep of new ORSet, current value: ${currentSetValue.elements}")
                  var set: ORSet[String] = currentSetValue
                  currentSetValue.elements.diff(newKeyValue._2)
                    .foreach(x => set = set.remove(x)(ClusterNode.getSystemSetup.ddSelfUniqueAddress))
                  log.debug(s"done prepping of new ORSet, result: ${set.elements}")
                  set
                })
              }
            })
        })
  }
}
