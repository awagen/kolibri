package de.awagen.kolibri.datatypes.collections.generators

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

class PermutatingIndexedGeneratorSpec extends UnitTestSpec {

  val generator1: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(5, x => Some(x))
  val generator2: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(5, x => Some(x + 1))
  val generator3: IndexedGenerator[Int] = ByFunctionNrLimitedIndexedGenerator(10, x => Some(x))

  val permutatingGen: IndexedGenerator[Seq[Int]] = PermutatingIndexedGenerator(Seq(generator1, generator2, generator3))
  val allValues: Seq[Seq[Int]] = permutatingGen.iterator.toSeq

  "PermutatingIndexedGenerator" must {

    "correctly calculate nr of elements" in {
      permutatingGen.nrOfElements mustBe 250
    }

    "correctly calculate part" in {
      permutatingGen.getPart(10, 250).iterator.toSeq mustBe allValues.slice(10, 250)
      permutatingGen.getPart(10, 240).iterator.toSeq mustBe allValues.slice(10, 240)
    }

    "correctly get single element" in {
      Range(0, 250).map(x => permutatingGen.get(x).get) mustBe allValues
    }

    "correctly apply mapping" in {
      permutatingGen.mapGen(x => x.mkString("-")).iterator.toSeq mustBe allValues.map(x => x.mkString("-"))
    }

  }

}
