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

package de.awagen.kolibri.datatypes.utils

import scala.collection.immutable

object SequenceUtils {


  /**
    * Compare two sequences based. Returns false in case either lengths dont match or the elements for a
    * given index do not share the same class or contain indeed different values.
    *
    * @param seq1
    * @param seq2
    * @return
    */
  def areSame(seq1: Seq[Any], seq2: Seq[Any]): Boolean = {
    var result: Boolean = true
    if (seq1.size != seq2.size) return false
    val tuples: immutable.Seq[(_, _)] = seq1.indices.map(x => (seq1(x), seq2(x))).toList
    for (tuple <- tuples) {
      result = result && (tuple._1.getClass == tuple._2.getClass) && (tuple match {
        case fl: (_, _) if fl._1.isInstanceOf[Float] => MathUtils.equalWithPrecision(fl._1.asInstanceOf[Float], fl._2.asInstanceOf[Float], 0.0001f)
        case fl: (_, _) if fl._1.isInstanceOf[Double] => MathUtils.equalWithPrecision(fl._1.asInstanceOf[Double], fl._2.asInstanceOf[Double], 0.0001)
        case fl => fl._1.equals(fl._2)
      })
    }
    result
  }

  def areSameSeq(seq1: Seq[Seq[Any]], seq2: Seq[Seq[Any]]): Boolean = {
    var isSame: Boolean = true
    if (seq1.size != seq2.size) isSame = false
    val vals: immutable.Seq[(Seq[Any], Seq[Any])] = seq1.indices.map(x => (seq1(x), seq2(x))).toList
    for (j <- seq1.indices) {
      if (!areSame(vals(j)._1, vals(j)._2)) isSame = false
    }
    isSame
  }

}
