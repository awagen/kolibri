package de.awagen.kolibri.base.types

trait Modifier[T] {

  def apply(a: T): T

}
