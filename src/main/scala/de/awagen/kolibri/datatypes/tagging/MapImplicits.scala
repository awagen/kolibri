package de.awagen.kolibri.datatypes.tagging

import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

object MapImplicits {

  val logger: Logger = LoggerFactory.getLogger(MapImplicits.getClass.toString)

  class TaggedMap[K, V](map: Map[K, V]) extends Map[K, V] with TaggedWithType[Tag] {
    override def removed(key: K): Map[K, V] = map.removed(key)

    override def updated[V1 >: V](key: K, value: V1): Map[K, V1] = map.updated(key, value)

    override def get(key: K): Option[V] = map.get(key)

    override def iterator: Iterator[(K, V)] = map.iterator
  }

  class MutableTaggedMap[K, V](map: mutable.Map[K, V]) extends mutable.Map[K, V] with TaggedWithType[Tag] {
    override def subtractOne(elem: K): MutableTaggedMap.this.type = {
      map.subtractOne(elem)
      this
    }

    override def addOne(elem: (K, V)): MutableTaggedMap.this.type = {
      map.addOne(elem)
      this
    }

    override def get(key: K): Option[V] = map.get(key)

    override def iterator: Iterator[(K, V)] = map.iterator
  }

  implicit class MapToTaggedWithTypeMap[K, V](val map: Map[K, V]) {

    def toTaggedWithTypeMap: Map[K, V] with TaggedWithType[Tag] = new TaggedMap(map)

  }

  implicit class MutableMapToTaggedWithTypeMap[K, V](val map: mutable.Map[K, V]) {

    def toTaggedWithTypeMap: mutable.Map[K, V] with TaggedWithType[Tag] = new MutableTaggedMap(map)

  }

}
