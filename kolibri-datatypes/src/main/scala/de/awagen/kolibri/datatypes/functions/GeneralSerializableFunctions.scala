package de.awagen.kolibri.datatypes.functions

import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1

object GeneralSerializableFunctions {

  def identity[A]: SerializableFunction1[A, A] = new SerializableFunction1[A, A] {
    override def apply(v1: A): A = v1
  }

}
