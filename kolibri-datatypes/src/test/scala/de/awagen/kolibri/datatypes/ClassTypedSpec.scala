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


package de.awagen.kolibri.datatypes

import de.awagen.kolibri.datatypes.testclasses.UnitTestSpec

class ClassTypedSpec extends UnitTestSpec {

  "ClassTyped" must {

    "be equal for same types" in {
      ClassTyped[String].equals(ClassTyped[String]) mustBe true
      ClassTyped[String].equals(ClassTyped[Int]) mustBe false
    }

    "have equal hashes" in {
      ClassTyped[String].hashCode().equals(ClassTyped[String].hashCode()) mustBe true
      ClassTyped[String].hashCode().equals(ClassTyped[Int].hashCode()) mustBe false
    }
  }

  "NamedClassTyped" must {

    "be equal only for same types and names" in {
      NamedClassTyped[Double]("name1").equals(NamedClassTyped[Double]("name1")) mustBe true
      NamedClassTyped[Double]("name1").equals(NamedClassTyped[Double]("name2")) mustBe false
      NamedClassTyped[Double]("name1").equals(NamedClassTyped[String]("name1")) mustBe false
    }

    "have only equal hashes in case of same types and names" in {
      NamedClassTyped[Double]("name1").hashCode().equals(NamedClassTyped[Double]("name1").hashCode()) mustBe true
      NamedClassTyped[Double]("name1").hashCode().equals(NamedClassTyped[Double]("name2").hashCode()) mustBe false
      NamedClassTyped[Double]("name1").hashCode().equals(NamedClassTyped[String]("name1").hashCode()) mustBe false
    }

  }

}
