package de.awagen.kolibri.storage.io.writer

import de.awagen.kolibri.datatypes.io.KolibriSerializable

object Writers {

  /**
   * Generic writer trait
   *
   * @tparam U - data to be persisted
   * @tparam V - targetIdentifier, e.g filename for FileWriter
   * @tparam W - return value on successful write completion
   */
  trait Writer[U, V, +W] extends KolibriSerializable {

    def write(data: U, targetIdentifier: V): Either[Exception, W]

    def delete(targetIdentifier: V): Either[Exception, Unit]

    def copyDirectory(dirPath: String, toDirPath: String): Unit

    def moveDirectory(dirPath: String, toDirPath: String): Unit

  }

  /**
   * Writer where targetIdentifier is giving the filename to write to
   *
   * @tparam T - data to be persisted
   * @tparam U - return value on successful write completion
   */
  trait FileWriter[T, +U] extends Writer[T, String, U]

}
