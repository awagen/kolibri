package de.awagen.kolibri.base.utils

object IterableUtils {

  def combineMaps[K, V](map1: Map[K, Seq[V]], map2: Map[K, Seq[V]], replace: Boolean): Map[K, Seq[V]] = {
    if (replace) map1 ++ map2 else map1 ++ map2.map { case (k, v) => k -> (map1.getOrElse(k, Seq.empty) ++ v) }

  }

}
