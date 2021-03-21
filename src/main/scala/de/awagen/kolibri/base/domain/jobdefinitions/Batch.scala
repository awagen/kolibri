package de.awagen.kolibri.base.domain.jobdefinitions

import de.awagen.kolibri.datatypes.collections.IndexedGenerator
import de.awagen.kolibri.datatypes.io.KolibriSerializable

case class Batch[V](batchNr: Int, data: IndexedGenerator[V]) extends KolibriSerializable
