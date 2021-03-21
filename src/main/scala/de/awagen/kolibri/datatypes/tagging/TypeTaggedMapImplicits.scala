package de.awagen.kolibri.datatypes.tagging

import de.awagen.kolibri.datatypes.ClassTyped
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import org.slf4j.{Logger, LoggerFactory}

import scala.reflect.runtime.universe

object TypeTaggedMapImplicits {

  val logger: Logger = LoggerFactory.getLogger(TypeTaggedMapImplicits.getClass.toString)

  class TaggedTypeTaggedMap(map: TypeTaggedMap) extends TypeTaggedMap with TaggedWithType[Tag] {
    override def put[T: universe.TypeTag, V](key: ClassTyped[V], value: T): Option[Any] = map.put(key, value)

    override def remove[T](key: ClassTyped[T]): Option[T] = map.remove(key)

    override def get[V](key: ClassTyped[V]): Option[V] = map.get(key)

    override def keys: Iterable[ClassTyped[Any]] = map.keys

    override def keySet: collection.Set[ClassTyped[Any]] = map.keySet
  }

  implicit class TypeTaggedMapToTaggedTypeTaggedMap(val map: TypeTaggedMap) {

    def toTaggedWithTypeMap: TypeTaggedMap with TaggedWithType[Tag] = new TaggedTypeTaggedMap(map)

  }

}
