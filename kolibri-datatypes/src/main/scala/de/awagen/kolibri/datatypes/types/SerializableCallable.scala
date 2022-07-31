/**
  * Copyright 2021 Andreas Wagenmann
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */


package de.awagen.kolibri.datatypes.types

import de.awagen.kolibri.datatypes.io.KolibriSerializable

import java.util.Objects

object SerializableCallable {

  @FunctionalInterface
  trait SerializableSupplier[+T] extends (() => T) with KolibriSerializable

  @FunctionalInterface
  trait SerializableFunction1[-T, +U] extends (T => U) with KolibriSerializable

  @FunctionalInterface
  trait SerializableFunction2[-T1, -T2, +R] extends ((T1, T2) => R) with KolibriSerializable

  @FunctionalInterface
  trait SerializableConsumer[T] extends (T => ()) with KolibriSerializable

  case class CachedSupplier[T](supplier: SerializableSupplier[T]) extends SerializableSupplier[T]{
    private[this] var value: T = _

    override def apply(): T = {
      if (Objects.isNull(value)) {
        value = supplier.apply()
      }
      value
    }
  }
}
