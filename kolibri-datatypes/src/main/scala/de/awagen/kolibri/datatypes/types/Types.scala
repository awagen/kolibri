package de.awagen.kolibri.datatypes.types

object Types {

  trait WithCount {

    def count: Int

  }

  trait WithWeight {

    def weight: Double

  }

  trait With[T] {

    def withInstance: T

  }


}
