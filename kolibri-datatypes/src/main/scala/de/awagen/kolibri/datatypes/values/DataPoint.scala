package de.awagen.kolibri.datatypes.values

import de.awagen.kolibri.datatypes.io.KolibriSerializable

object DataPoint {

  def apply[T](weight: Double, data: T): DataPoint[T] ={
    new DataSample[T](weight, data)
  }

}

trait DataPoint[+T] extends KolibriSerializable {

  def weight: Double

  def data: T

}

case class DataSample[+T](weight: Double, data: T) extends DataPoint[T]