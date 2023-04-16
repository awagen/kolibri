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


package de.awagen.kolibri.storage.io.reader

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

class AWSS3AccessSpec extends UnitTestSpec with BeforeAndAfterAll {

  val localstackImage: DockerImageName = DockerImageName.parse("localstack/localstack:0.11.3")
  val localstack: LocalStackContainer = new LocalStackContainer(localstackImage).withServices(LocalStackContainer.Service.S3)
  val testBucketName: String = "testbucket"

  private[this] var s3: AmazonS3 = _

  override def beforeAll(): Unit = {
    // needs to be started before we can pick endpoint config and credential provider for creation of s3 client
    localstack.start()
    s3 = getAndSetupAmazonS3Client(
      localstack.getEndpointConfiguration(LocalStackContainer.Service.S3),
      localstack.getDefaultCredentialsProvider
    )
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
    client.createBucket(testBucketName)
    client.putObject(testBucketName, "dir1/dir2/dir3/dir3_1", "testcontent1")
    client.putObject(testBucketName, "dir1/dir2/dir3/dir3_2", "testcontent2")
    client.putObject(testBucketName, "dir1/dir2/dir3/file.txt", "testcontent3")
    client.putObject(testBucketName, "dir1/dir2/dir3/dir4/file1.txt", "testcontent3")
    client
  }

  "AwsS3DirectoryReader" should {

    "correctly read directories from bucket" in {
      // given
      val directoryReader = AwsS3DirectoryReader(
        bucketName = testBucketName,
        dirPath = "dir1",
        region = Regions.valueOf(localstack.getRegion.toUpperCase.replace("-", "_")),
        fileFilter = x => !x.split("/").last.contains("."),
        awsS3Client = Some(s3)
      )
      // when
      val dataFilesAndFolder: Seq[String] = directoryReader.listResources("dir2/dir3", _ => true)
      val dataOnlyFolders: Seq[String] = directoryReader.listResources("dir2", _ => true)
      // then
      dataFilesAndFolder.size mustBe 3
      dataOnlyFolders.size mustBe 1
      dataFilesAndFolder.toSet mustBe Set("dir1/dir2/dir3/dir3_1", "dir1/dir2/dir3/dir3_2", "dir1/dir2/dir3/dir4/")
      dataOnlyFolders mustBe Seq("dir1/dir2/dir3/")
    }
  }

  "AwsS3FileReader" should {

    "correctly read file from bucket" in {
      // given
      val fileReader = AwsS3FileReader(
        bucketName = testBucketName,
        dirPath = "dir1",
        region = Regions.valueOf(localstack.getRegion.toUpperCase.replace("-", "_")),
        awsS3Client = Some(s3)
      )
      // when, then
      fileReader.read("dir2/dir3/dir3_1").mkString("\n") mustBe "testcontent1"
    }

  }

}
