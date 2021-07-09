package de.awagen.kolibri.base.traits

import de.awagen.kolibri.datatypes.io.KolibriSerializable

object Traits {

  trait WithBatchNr extends KolibriSerializable {

    val batchNr: Int

  }

}
