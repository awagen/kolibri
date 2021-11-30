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

package de.awagen.kolibri.base.actors.tracking

import akka.actor.{Actor, ActorLogging, Props}
import de.awagen.kolibri.base.actors.tracking.ThroughputActor._

import scala.collection.immutable.ListMap
import scala.concurrent.duration.Duration


object ThroughputActor {

  case class IndexedCount(index: Int, var value: Int)

  def props(duration: Duration, keepMaxElements: Int): Props = {
    Props(new ThroughputActor(duration, keepMaxElements))
  }

  sealed trait ThroughputActorCmd

  sealed trait ThroughputActorEvent

  sealed case class ProvideThroughputForStage(stage: String) extends ThroughputActorCmd

  sealed case class AddForStage(stage: String) extends ThroughputActorCmd

  sealed case class PrintState(lastN: Int) extends ThroughputActorCmd

  sealed case class ThroughputForStage(stage: String,
                                       throughputPerTimeUnit: Vector[IndexedCount],
                                       totalSoFar: Int) extends ThroughputActorEvent

}

class ThroughputActor(val duration: Duration,
                      val keepMaxElements: Int = 50) extends Actor with ActorLogging {

  val startTimeInMs: Long = System.currentTimeMillis()
  private var totalStageMap: Map[String, Int] = ListMap.empty
  private var intervalStageMap: Map[String, Vector[IndexedCount]] = Map.empty

  def getCurrentIntervalIndex: Int = {
    val timePast: Long = System.currentTimeMillis() - startTimeInMs
    (timePast / duration.toMillis).toInt
  }

  private[this] def extendVectorWithZerosTillIndex(seq: Vector[IndexedCount], requiredMaxIndex: Int): Vector[IndexedCount] = {
    var newSeq = seq
    val currentIndex = if (newSeq.isEmpty) -1 else newSeq.last.index
    val indexDiff = if (currentIndex >= requiredMaxIndex) 0 else requiredMaxIndex - currentIndex
    if (indexDiff > 0) Range(1, indexDiff + 1).foreach(x => newSeq = newSeq :+ new IndexedCount(currentIndex + x, 0))
    newSeq
  }

  private[this] def addOneForStage(stageName: String): Unit = {
    val bin = getCurrentIntervalIndex
    var updatedIntervals: Vector[IndexedCount] = intervalStageMap.getOrElse(stageName, Vector.empty[IndexedCount])
    updatedIntervals = extendVectorWithZerosTillIndex(updatedIntervals, bin)
    val elementToUpdate = updatedIntervals.find(x => x.index == bin)
    elementToUpdate.foreach(x =>
      updatedIntervals = updatedIntervals.updated(updatedIntervals.indexOf(x), IndexedCount(x.index, x.value + 1))
    )
    updatedIntervals = updatedIntervals.drop(Math.max(updatedIntervals.size - keepMaxElements, 0))
    intervalStageMap = intervalStageMap + (stageName -> updatedIntervals)
    totalStageMap = totalStageMap + (stageName -> (totalStageMap.getOrElse[Int](stageName, 0) + 1))
  }

  def formatTotalPerStage: String = {
    val buff = new StringBuilder()
    totalStageMap.keys.to(LazyList).sorted.foreach(x => {
      buff.append(s"stage:$x - sample total: ${totalStageMap(x)}")
      buff.append("\n")
    })
    buff.toString().trim
  }

  def formatTimeSeriesData(lastN: Int): String = {
    val buff = new StringBuilder()
    intervalStageMap.keys.to(LazyList).sorted.foreach(x => {
      buff.append(s"stage:$x\n")
      var currentState: Seq[IndexedCount] = Seq(intervalStageMap(x): _*)
      currentState = currentState.slice(Math.max(currentState.size - lastN, 0), currentState.size)
      currentState.foreach(entry => {
        buff.append(s"${entry.index} : ${entry.value}\t")
      })
      buff.append("\n")
    })
    buff.toString().trim
  }

  override def receive: Receive = {
    case e: ProvideThroughputForStage =>
      sender() ! ThroughputForStage(
        e.stage,
        intervalStageMap.getOrElse(e.stage, Vector.empty),
        totalStageMap.getOrElse(e.stage, 0))
    case e: AddForStage =>
      addOneForStage(e.stage)
    case PrintState(lastN: Int) =>
      log.info(formatTotalPerStage)
      log.info(formatTimeSeriesData(lastN))


  }

}
