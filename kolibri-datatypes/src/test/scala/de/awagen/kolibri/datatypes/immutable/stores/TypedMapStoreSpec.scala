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

package de.awagen.kolibri.datatypes.immutable.stores

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec
import de.awagen.kolibri.datatypes.types.ClassTyped
import de.awagen.kolibri.datatypes.utils.TestHelper.TestTypedClass

import scala.collection.immutable
import scala.reflect.runtime.universe._

class TypedMapStoreSpec extends UnitTestSpec {

  object TestObjects {

    sealed trait Container[+T]

    case class Value1[+T](value: T) extends Container[T]

  }

  "TypedMapStore" should {

    "correctly provide type check for data" in {
      //given
      val strSeq: Seq[String] = Seq("a", "b")
      val intSeq: Seq[Int] = Seq(1, 2, 3)
      val map = TypedMapStore(immutable.Map.empty)
      //when
      val shouldBeTrue_1: Boolean = map.isOfType(strSeq, typeOf[Seq[String]])
      val shouldBeFalse_1: Boolean = map.isOfType(strSeq, typeOf[Seq[Int]])
      val shouldBeTrue_2: Boolean = map.isOfType(intSeq, typeOf[Seq[Int]])
      //then
      shouldBeTrue_1 mustBe true
      shouldBeTrue_2 mustBe true
      shouldBeFalse_1 mustBe false

    }

    "correctly store or reject data" in {
      //given
      val seqStrKey1 = TestTypedClass[Seq[String]]("sk1")
      val seqStrKey2 = TestTypedClass[Seq[String]]("sk2")
      val seqIntKey1 = TestTypedClass[Seq[Int]]("ik1")
      val strSeq: Seq[String] = Seq("a", "b")
      val intSeq: Seq[Int] = Seq(1, 2, 3)
      var map: TypeTaggedMap = TypedMapStore(immutable.Map.empty)
      //when
      map = map.put(seqStrKey1, strSeq)._2
      map = map.put(seqStrKey2, intSeq)._2
      map = map.put(seqIntKey1, intSeq)._2
      //then
      map.keys.size mustBe 2
      map.get(seqStrKey1) mustBe Some(strSeq)
      map.get(seqIntKey1) mustBe Some(intSeq)
      map.get(seqStrKey2) mustBe None
    }

    "correctly match types" in {
      // given
      var map: TypeTaggedMap = TypedMapStore(immutable.Map.empty)
      val key = ClassTyped[String]
      // when
      map = map.put(key, "1,2,3")._2
      // then
      map.get(key).get mustBe "1,2,3"

    }

    "correctly cover subtypes" in {
      // given
      var map: TypeTaggedMap = TypedMapStore(Map.empty)
      val key = ClassTyped[TestObjects.Container[Unit]]
      // when
      map = map.put(key, TestObjects.Value1(()))._2
      // then
      map.get(key).get mustBe TestObjects.Value1(())
    }
  }

}
