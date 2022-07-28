/**
 * Copyright 2022 Andreas Wagenmann
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


package de.awagen.kolibri.base.serialization

import akka.serialization.{SerializationExtension, Serializers}
import akka.testkit.TestKit
import de.awagen.kolibri.base.actors.KolibriTestKitNoCluster
import de.awagen.kolibri.base.processing.modifiers.ParameterValues.ParameterValueMapping
import de.awagen.kolibri.base.processing.modifiers.ParameterValuesSpec
import de.awagen.kolibri.base.processing.modifiers.ParameterValuesSpec.{mappedValue1, mappedValue2}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class SerializationSpec extends KolibriTestKitNoCluster
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
  }

  def serializeAndBack(original: AnyRef): AnyRef = {
    // Get the Serialization Extension
    val serialization = SerializationExtension(system)
    // Turn it into bytes, and retrieve the serializerId and manifest, which are needed for deserialization
    val bytes = serialization.serialize(original).get
    val serializerId = serialization.findSerializerFor(original).identifier
    val manifest = Serializers.manifestFor(serialization.findSerializerFor(original), original)
    // Turn it back into an object
    serialization.deserialize(bytes, serializerId, manifest).get
  }

  "Serialization" must {

    "work properly on ParameterValueMappings" in {
      // given
      val original = new ParameterValueMapping(keyValues = ParameterValuesSpec.parameterValues, mappedValues = Seq(mappedValue1, mappedValue2),
        mappingKeyValueAssignments = Seq((1, 2)))
      // when, then
      serializeAndBack(original)
    }

  }

}
