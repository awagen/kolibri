package de.awagen.kolibri.datatypes.values.aggregation

import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.values.DataPoint

trait AggregateValue[+A] extends KolibriSerializable {

  def numSamples: Int

  def weight: Double

  def value: A

  def weighted(weight: Double): AggregateValue[A]

  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[AggregateValue[A]]) {
      false
    }
    else {
      val other = obj.asInstanceOf[AggregateValue[A]]
      this.numSamples == other.numSamples && this.weight == other.weight && this.value == other.value
    }
  }

  def emptyCopy(): AggregateValue[A]

  def add[B >: A](other: AggregateValue[B]): AggregateValue[A]

  def add[B >: A](otherValue: DataPoint[B]): AggregateValue[A]

}
