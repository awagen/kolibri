package de.awagen.kolibri.storage.io.reader

import de.awagen.kolibri.datatypes.io.KolibriSerializable

import scala.io.Source

trait Reader[IdType, +ReadType] extends KolibriSerializable {

  def getSource(dataIdentifier: IdType): Source

  def read(dataIdentifier: IdType): ReadType

}
