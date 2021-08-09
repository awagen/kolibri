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


package de.awagen.kolibri.datatypes.collections.generators

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

class PartitionByGroupIndexedGeneratorSpec extends UnitTestSpec {

  val generator1: IndexedGenerator[String] = ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("a", "b", "c"))
  val generator2: IndexedGenerator[String] = ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("d", "e", "f"))
  val generator3: IndexedGenerator[String] = ByFunctionNrLimitedIndexedGenerator.createFromSeq(Seq("g", "h", "i", "j"))
  val partitionedGenerator: IndexedGenerator[String] = PartitionByGroupIndexedGenerator(Seq(generator1, generator2, generator3))

  "PartitionByGroupIndexedGenerator" must {

    "correctly partition" in {
      partitionedGenerator.iterator.toSeq mustBe Seq("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")
      partitionedGenerator.partitions.iterator.toSeq.map(x => x.iterator.toSeq) mustBe Seq(
        Seq("a", "b", "c"),
        Seq("d", "e", "f"),
        Seq("g", "h", "i", "j")
      )
    }
  }

}
