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

package de.awagen.kolibri.storage.io.writer.base

import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.{DeleteObjectRequest, ObjectMetadata, PutObjectRequest, PutObjectResult}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.{AmazonServiceException, SdkClientException}
import de.awagen.kolibri.storage.io.reader.ReaderUtils.normalizeBucketPath
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory}

import java.nio.charset.StandardCharsets
import java.util.Objects
import scala.jdk.javaapi.CollectionConverters


case class AwsS3FileWriter(bucketName: String,
                           dirPath: String,
                           region: Regions,
                           contentType: String = "text/plain; charset=UTF-8",
                           delimiter: String = "/") extends FileWriter[String, PutObjectResult] {

  val dirPathNormalized: String = normalizeBucketPath(dirPath, delimiter)

  private val logger: Logger = LoggerFactory.getLogger(AwsS3FileWriter.getClass)

  private[this] var s3Client: AmazonS3 = null

  // workaround for serialization
  def setS3ClientIfNotSet(): Unit = {
    synchronized {
      if (Objects.isNull(s3Client)) {
        s3Client = AmazonS3ClientBuilder.standard().withRegion(region).build()
      }
    }
  }
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
      setS3ClientIfNotSet()
      logger.debug(s"writing data for identifier: $targetIdentifier")
      //file upload as new object with ContentType and title
      val metaData: ObjectMetadata = baseMetaData()
      metaData.setContentLength(data.getBytes(StandardCharsets.UTF_8).length)
      val contentStream = IOUtils.toInputStream(data, "UTF-8")
      val key = s"$dirPathNormalized$targetIdentifier".stripSuffix(delimiter)
      val putObjectRequest: PutObjectRequest = new PutObjectRequest(bucketName, key , contentStream, metaData)
      val result = s3Client.putObject(putObjectRequest)
      logger.debug(s"write result: $result")
      Right(result)
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

  override def delete(targetIdentifier: String): Either[Exception, Unit] = {
    try {
      val path = s"$dirPathNormalized$targetIdentifier".stripSuffix(delimiter)
      logger.debug(s"Trying to delete path '$path' in bucket $bucketName")
      Right(s3Client.deleteObject(new DeleteObjectRequest(bucketName, path)))
    }
    catch {
      case e: AmazonServiceException =>
        // call transmitted correctly, but S3 could not process it
        logger.error("S3 failed to process delete request", e)
        Left(e)
      case e: SdkClientException =>
        // S3 not reachable, or client unable to parse response from S3
        logger.error("S3 not reachable or unable to client unable to parse response of delete request", e)
        Left(e)
    }
  }

  private def deleteObjectsWithPrefix(objectPrefix: String): Unit = {
    try {
      val files = listFilesWithPrefix(objectPrefix)
      files.foreach(file => {
        s3Client.deleteObject(new DeleteObjectRequest(bucketName, file))
      })
    }
    catch {
      case e: AmazonServiceException =>
        // call transmitted correctly, but S3 could not process it
        logger.error("S3 failed to process delete request", e)
        throw e
      case e: SdkClientException =>
        // S3 not reachable, or client unable to parse response from S3
        logger.error("S3 not reachable or unable to client unable to parse response of delete request", e)
        throw e
    }
  }

  /**
   * Lists the directory contents in the passed path. Path is relative to the
   * base bath defined for the app. The returned files start with the base path,
   * but do not contain the bucket
   */
  private def listFilesWithPrefix(prefix: String): Seq[String] = {
    val objectPrefix = s"$dirPathNormalized$prefix".stripSuffix(delimiter)
    val objectSummaries = CollectionConverters.asScala(s3Client.listObjects(bucketName, objectPrefix).getObjectSummaries).toSeq
    objectSummaries.map(x => x.getKey)
  }

  // we pick all objects with the prefix of the folder
  // and copy each one (see https://stackoverflow.com/questions/12518524/amazon-s3-copy-the-directory-to-another-directory)
  override def copyDirectory(dirPath: String, toDirPath: String): Unit = {
    try {
      val fromDirPrefix = s"$dirPathNormalized$dirPath".stripSuffix(delimiter)
      val fromDirTopLevelFolder = fromDirPrefix.split(delimiter).last.stripSuffix(delimiter)
      val containedFiles = listFilesWithPrefix(dirPath)
      val newDirPrefix = s"$dirPathNormalized${toDirPath.stripSuffix(delimiter)}${delimiter}$fromDirTopLevelFolder"
      containedFiles.foreach(file => {
        val newFile = file.replace(fromDirPrefix, newDirPrefix)
        logger.debug(s"trying to copy '$file' to '$newFile' for bucket '$bucketName'")
        s3Client.copyObject(bucketName, file, bucketName, newFile)
      })
    }
    catch {
      case e: AmazonServiceException =>
        // call transmitted correctly, but S3 could not process it
        logger.error("S3 failed to process copy request", e)
        throw e
      case e: SdkClientException =>
        // S3 not reachable, or client unable to parse response from S3
        logger.error("S3 not reachable or unable to client unable to parse response of copy request", e)
        throw e
    }
  }

  override def moveDirectory(dirPath: String, toDirPath: String): Unit = {
    copyDirectory(dirPath, toDirPath)
    deleteDirectory(dirPath)
  }

  override def deleteDirectory(dirPath: String): Unit =
    deleteObjectsWithPrefix(dirPath)
}
