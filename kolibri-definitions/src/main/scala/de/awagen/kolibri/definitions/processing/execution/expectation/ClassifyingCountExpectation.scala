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

package de.awagen.kolibri.definitions.processing.execution.expectation

import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1

import scala.collection.mutable

/**
  * Classifying count expectation, that is every message accepted (accept(msg) call) is classified (if no criteria
  * match, its ignored) and counts are collected. Is fulfilled when all expectations match. To calculate the nr of
  * expected messages per class, the expectation maintains the mapping how many it expects per class per single element
  * (element here e.g would represent one processing unit in a flow, where we might expect multiple messages back for)
  * is multiplied by nrElements
  *
  * @param classifier          -  classifier for the messages accept(msg) is called on
  * @param expectedClassCounts - the class counts
  */
case class ClassifyingCountExpectation(classifier: Map[String, SerializableFunction1[Any, Boolean]],
                                       expectedClassCounts: Map[String, Int]) extends Expectation[Any] {

  private[this] var currentCounts: mutable.Map[String, Int] = mutable.Map(expectedClassCounts.map(x => (x._1, 0)).toSeq: _*)
  private[this] var unfulfilledClasses: Seq[String] = expectedClassCounts.keys.filter(x => expectedClassCounts(x) > 0).toSeq
  // assert that for each non-zero expectation the key must also be in classifier, otherwise there is no
  // way this expectation can be met
  val unmeetableExpectations: Seq[String] = unfulfilledClasses.filter(x => !classifier.keys.toSeq.contains(x))
  assert(unmeetableExpectations.isEmpty, s"unmeatable expectations for classes: $unmeetableExpectations")

  override def init: Unit = ()

  private def setCurrentCounts(current_counts: Map[String, Int]): Unit = {
    this.currentCounts = mutable.Map(current_counts.toSeq: _*)
  }

  def getUnfulfilledClasses: Seq[String] = unfulfilledClasses

  private def setUnfulfilledClasses(classes: Seq[String]): Unit = {
    this.unfulfilledClasses = classes
  }

  override def succeeded: Boolean = unfulfilledClasses.isEmpty

  override def accept[TT >: Any](element: TT): Unit = {
    if (unfulfilledClasses.isEmpty) {
      return
    }
    val suitableClasses: Seq[String] = classifier.filter(x => x._2.apply(element)).keys.toSeq
    suitableClasses.foreach(x => {
      val old_count = currentCounts(x)
      val new_value = old_count + 1
      currentCounts(x) = new_value
      if (new_value >= expectedClassCounts(x)) {
        unfulfilledClasses = unfulfilledClasses.filter(y => y != x)
      }
    })
  }

  override def statusDesc: String = {
    Seq(
      s"unmeetable expectation classes: $unmeetableExpectations",
      s"unfulfilled classes: $unfulfilledClasses",
      s"currentCounts: $currentCounts",
      s"expectedClassCounts: $expectedClassCounts"
    ).mkString("\n")
  }

  override def deepCopy: Expectation[Any] = {
    val copied = this.copy()
    copied.setCurrentCounts(Map(this.currentCounts.toSeq: _*))
    copied.setUnfulfilledClasses(this.unfulfilledClasses)
    copied
  }

}
