package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.datatypes.types.JsonStructDefs.StructDef

trait WithStructDef {

  def structDef: StructDef[_]

}
