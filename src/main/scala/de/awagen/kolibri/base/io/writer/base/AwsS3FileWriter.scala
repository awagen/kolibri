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

package de.awagen.kolibri.base.io.writer.base

import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.{ObjectMetadata, PutObjectRequest, PutObjectResult}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.{AmazonServiceException, SdkClientException}
import de.awagen.kolibri.base.io.writer.Writers.FileWriter
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory}


case class AwsS3FileWriter(bucketName: String,
                           dirPath: String,
                           region: Regions,
                           contentType: String = "text/csv") extends FileWriter[String, PutObjectResult] {

  private val logger: Logger = LoggerFactory.getLogger(AwsS3FileWriter.getClass)

  val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard().withRegion(region).build()

  def baseMetaData(): ObjectMetadata = {
    val metaData: ObjectMetadata = new ObjectMetadata()
    metaData.setContentType(contentType)
    metaData
  }

  /**
    * @param data             - the data to upload to key in s3 bucket
    * @param targetIdentifier - the filename in the bucket the content is stored in
    *                         (within [bucketname]/[dirPath]/[filename])
    * @return Either[Exception, PutObjectResult]
    */
  override def write(data: String, targetIdentifier: String): Either[Exception, PutObjectResult] = {
    try {
      //file upload as new object with ContentType and title
      val metaData: ObjectMetadata = baseMetaData()
      val putObjectRequest: PutObjectRequest = new PutObjectRequest(bucketName,
        s"${dirPath.stripSuffix("/")}/$targetIdentifier",
        IOUtils.toInputStream(data, "UTF-8"), metaData)
      Right(s3Client.putObject(putObjectRequest))
    }
    catch {
      case e: AmazonServiceException =>
        logger.error(s"Failed writing to key '$targetIdentifier' in s3Bucket $bucketName", e)
        Left(e)
      case e: SdkClientException =>
        logger.error(s"Failed writing to key '$targetIdentifier' in s3Bucket $bucketName", e)
        Left(e)
    }
  }

}
