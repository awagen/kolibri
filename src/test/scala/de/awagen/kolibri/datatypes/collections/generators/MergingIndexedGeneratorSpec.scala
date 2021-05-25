package de.awagen.kolibri.datatypes.collections.generators

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec


class MergingIndexedGeneratorSpec extends UnitTestSpec {

  val generator1: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(2, x => Some(x))
  val generator2: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(2, x => Some(x + 1))
  val generator3: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(3, x => Some(x))

  val combined1And2Generator: IndexedGenerator[Seq[Int]] = NthIsNthForEachIndexedGenerator(Seq(generator1, generator2))
  val mergingGenerator: IndexedGenerator[Seq[Int]] = MergingIndexedGenerator[Seq[Int], Int, Seq[Int]](combined1And2Generator, generator3,
    (seq1, el2) => seq1 :+ el2)


  "MergingIndexedGenerator" must {

    "correctly map elements" in {
      mergingGenerator.mapGen(x => x.mkString(",")).iterator.toSeq mustBe Seq("0,1,0", "1,2,0", "0,1,1", "1,2,1", "0,1,2", "1,2,2")
    }

    "correctly generate part" in {
      // given
      val fullSeq = mergingGenerator.iterator.toSeq
      // when, then
      mergingGenerator.getPart(2, mergingGenerator.size).iterator.toSeq mustBe fullSeq.slice(2, mergingGenerator.size)
    }

    "should reduce endIndex to max index if exceeding" in {
      // given
      val fullSeq = mergingGenerator.iterator.toSeq
      // when, then
      mergingGenerator.getPart(2, 100).iterator.toSeq mustBe fullSeq.slice(2, mergingGenerator.size)
    }

    "correctly provide iterator for whole" in {
      mergingGenerator.iterator.toSeq mustBe Seq(Seq(0, 1, 0), Seq(1, 2, 0), Seq(0, 1, 1), Seq(1, 2, 1), Seq(0, 1, 2), Seq(1, 2, 2))
    }


    "should throw assertion exception if startIndex < 0" in {
      assertThrows[AssertionError](mergingGenerator.getPart(-1, 3))
    }

  }

}
