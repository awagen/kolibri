package de.awagen.kolibri.base.io.json

import de.awagen.kolibri.base.domain.TaskDataKeys.METRICS
import de.awagen.kolibri.base.domain.jobdefinitions.JobDefinitions.{OrderedMultiValuesRunnableJobDefinition, OrderedMultiValuesRunnableTaskJobDefinition}
import de.awagen.kolibri.base.domain.jobdefinitions.MapTransformerFlows.IDENTITY
import de.awagen.kolibri.base.domain.jobdefinitions.RunnableExpectationGenerators.ONE_FOR_ONE
import de.awagen.kolibri.base.domain.jobdefinitions.TaskDefinitions.NULLTASK
import de.awagen.kolibri.base.io.json.JobDefinitionsJsonProtocol._
import de.awagen.kolibri.base.io.json.JsonTestObjects.{ORDERED_MULTIVALUES_RUNNABLE_JOB, ORDERED_MULTIVALUES_RUNNABLE_TASKJOB}
import de.awagen.kolibri.base.processing.execution.job.ActorRunnableSinkType.IGNORE_SINK
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import spray.json._


class JobDefinitionsJsonProtocolSpec extends UnitTestSpec {

  "JobDefinitionsJsonProtocol" must {

    "correctly parse json to OrderedMultiValuesRunnableJobDefinition" in {
      // given
      val json = ORDERED_MULTIVALUES_RUNNABLE_JOB
      val jsValue: JsValue = json.parseJson
      // when
      val jobDef: OrderedMultiValuesRunnableJobDefinition = jsValue.convertTo[OrderedMultiValuesRunnableJobDefinition]
      // then
      jobDef.jobId mustBe "job1"
      jobDef.allowedTimeForJobInSeconds mustBe 20
      jobDef.allowedTimePerBatchInSeconds mustBe 10
      jobDef.returnType mustBe IGNORE_SINK
      jobDef.expectationGenerators mustBe ONE_FOR_ONE
      jobDef.processingActorProps mustBe None
      jobDef.transformer mustBe IDENTITY
      jobDef.batchGenerator.paramNameToSplitBy mustBe "q"
      jobDef.dataProvider.data.getParameterNameSequence mustBe Seq("test")
    }

    "correctly parse json to OrderedMultiValuesRunnableTaskJobDefinition" in {
      // given
      val json = ORDERED_MULTIVALUES_RUNNABLE_TASKJOB
      val jsValue: JsValue = json.parseJson
      // when
      val jobDef: OrderedMultiValuesRunnableTaskJobDefinition = jsValue.convertTo[OrderedMultiValuesRunnableTaskJobDefinition]
      // then
      jobDef.jobId mustBe "job1"
      jobDef.dataProvider.data.getParameterNameSequence mustBe Seq("test")
      jobDef.batchGenerator.paramNameToSplitBy mustBe "q"
      jobDef.resultDataKey mustBe METRICS
      jobDef.tasks mustBe Seq(NULLTASK)
      jobDef.allowedTimePerBatchInSeconds mustBe 10
      jobDef.allowedTimeForJobInSeconds mustBe 20
    }

  }

}
