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


package de.awagen.kolibri.base.io.reader

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import de.awagen.kolibri.base.testclasses.UnitTestSpec
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

class AwsS3DirectoryReaderSpec extends UnitTestSpec with BeforeAndAfterAll {

  val localstackImage: DockerImageName = DockerImageName.parse("localstack/localstack:0.11.3")
  val localstack: LocalStackContainer = new LocalStackContainer(localstackImage).withServices(LocalStackContainer.Service.S3)

  private[this] var s3: AmazonS3 = _

  override def beforeAll(): Unit = {
    // needs to be started before we can pick endpoint config and credential provider for creation of s3 client
    localstack.start()
    s3 = getAndSetupAmazonS3Client(localstack.getEndpointConfiguration(LocalStackContainer.Service.S3), localstack.getDefaultCredentialsProvider)
  }

  override def afterAll(): Unit = {
    s3.shutdown()
    localstack.stop()
  }

  def getAndSetupAmazonS3Client(endpointConfig: AwsClientBuilder.EndpointConfiguration, credentialProvider: AWSCredentialsProvider): AmazonS3 = {
    val client: AmazonS3 = AmazonS3ClientBuilder.standard()
      .withEndpointConfiguration(endpointConfig)
      .withCredentials(credentialProvider)
      .build()
    client.createBucket("testbucket")
    client.putObject("testbucket", "dir1/dir2/dir3/dir3_1", "testcontent1")
    client.putObject("testbucket", "dir1/dir2/dir3/dir3_2", "testcontent2")
    client.putObject("testbucket", "dir1/dir2/dir3/file.txt", "testcontent3")
    client
  }

  "AwsS3DirectoryReader" should {

    "correctly read directories from bucket" in {
      // given
      val directoryReader = AwsS3DirectoryReader(bucketName = "testbucket",
        dirPath = "dir1",
        region = Regions.DEFAULT_REGION,
        fileFilter = x => !x.split("/").last.contains("."),
        awsS3Client = Some(s3)
      )
      // when
      val data: Seq[String] = directoryReader.listResources("dir2/dir3", _ => true)
      // then
      data.size mustBe 2
      data mustBe Seq("dir1/dir2/dir3/dir3_1", "dir1/dir2/dir3/dir3_2")
    }

  }

}
