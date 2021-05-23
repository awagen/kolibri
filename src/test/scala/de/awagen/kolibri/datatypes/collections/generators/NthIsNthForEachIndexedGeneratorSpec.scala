package de.awagen.kolibri.datatypes.collections.generators

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

class NthIsNthForEachIndexedGeneratorSpec extends UnitTestSpec {

  val generator1: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(5, x => Some(x))
  val generator2: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(5, x => Some(x + 1))
  val generator3: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(10, x => Some(x))

  val combinedGenerator: IndexedGenerator[Seq[Int]] = NthIsNthForEachIndexedGenerator(Seq(generator1, generator2, generator3))

  "NthIsNthForEachIndexedGenerator" must {

    "correctly determine nr of elements as min of all" in {
      // given, when, then
      combinedGenerator.nrOfElements mustBe 5
    }

    "correctly determine elements" in {
      // given, when, then
      combinedGenerator.iterator.toSeq mustBe Seq(Seq(0,1,0), Seq(1,2,1), Seq(2,3,2), Seq(3,4,3), Seq(4,5,4))
    }

    "correctly get part" in {
      // given, when
      val partGen1 = combinedGenerator.getPart(0,3)
      val partGen2 = combinedGenerator.getPart(2,10)
      // then
      partGen1.iterator.toSeq mustBe Seq(Seq(0,1,0), Seq(1,2,1), Seq(2,3,2))
      partGen2.iterator.toSeq mustBe Seq(Seq(2,3,2), Seq(3,4,3), Seq(4,5,4))
    }

    "correctly calculate single elements" in {
      // given, when
      val elements = Range(0,7).map(x => combinedGenerator.get(x))
      // then
      elements mustBe Seq(Some(Seq(0,1,0)), Some(Seq(1,2,1)), Some(Seq(2,3,2)),
        Some(Seq(3,4,3)), Some(Seq(4,5,4)), None, None)
    }

    "correctly apply mapping" in {
      // given, when
      val strElements = combinedGenerator.mapGen(x => x.mkString(",")).iterator.toSeq
      // then
      strElements mustBe Seq("0,1,0", "1,2,1", "2,3,2", "3,4,3", "4,5,4")
    }

  }

}
