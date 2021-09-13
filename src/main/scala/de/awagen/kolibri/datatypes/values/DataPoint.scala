package de.awagen.kolibri.datatypes.values

import de.awagen.kolibri.datatypes.io.KolibriSerializable

trait DataPoint[T] extends KolibriSerializable {

  def weight: Double

  def data: T

}
