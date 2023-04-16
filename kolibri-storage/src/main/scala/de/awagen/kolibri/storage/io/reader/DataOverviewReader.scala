package de.awagen.kolibri.storage.io.reader

import de.awagen.kolibri.datatypes.io.KolibriSerializable

trait DataOverviewReader extends KolibriSerializable {

  // TODO: change return type to classification in files and directories
  def listResources(locationIdentifier: String, dataIdentifierFilter: String => Boolean): Seq[String]

}
