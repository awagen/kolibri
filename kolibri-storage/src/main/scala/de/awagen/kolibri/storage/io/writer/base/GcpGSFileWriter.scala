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

import com.google.api.gax.paging.Page
import com.google.cloud.storage.Storage.CopyRequest
import com.google.cloud.storage._
import de.awagen.kolibri.storage.io.reader.ReaderUtils.normalizeBucketPath
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.IteratorHasAsScala

/**
 * A GCP file writer writing data to google cloud storage
 *
 * @param bucketName - name of bucket to write to
 * @param dirPath    - the path within the bucket to write to, mimicking a folder structure
 * @param projectID  - the project id for which the bucket was created
 */
case class GcpGSFileWriter(bucketName: String,
                           dirPath: String,
                           projectID: String,
                           delimiter: String = "/") extends FileWriter[String, Unit] {
  val dirPathNormalized: String = normalizeBucketPath(dirPath, delimiter)

  private val logger: Logger = LoggerFactory.getLogger(GcpGSFileWriter.getClass)

  val storage: Storage = StorageOptions.newBuilder
    .setProjectId(projectID).build.getService

  override def write(data: String, targetIdentifier: String): Either[Exception, Unit] = {
    val targetIdentifierNormalized: String = targetIdentifier.stripPrefix(delimiter)
    val objectName = s"$dirPathNormalized$targetIdentifierNormalized".stripPrefix(delimiter)
    val blobId: BlobId = BlobId.of(bucketName, objectName)
    val blobInfo: BlobInfo = BlobInfo.newBuilder(blobId).build
    try {
      storage.create(blobInfo, data.getBytes("UTF-8"))
      Right(())
    }
    catch {
      case e: Exception => Left(e)
    }

  }

  override def delete(targetIdentifier: String): Either[Exception, Unit] = {
    val targetIdentifierNormalized: String = targetIdentifier.stripPrefix(delimiter)
    val objectName = s"$dirPathNormalized$targetIdentifierNormalized".stripPrefix(delimiter)
    val blobId: BlobId = BlobId.of(bucketName, objectName)
    try {
      storage.delete(blobId)
      Right(())
    }
    catch {
      case e: Exception => Left(e)
    }
  }

  /**
   * List objects with given prefix.
   * NOTE: if the option Storage.BlobListOption.delimiter(delimiter) is set,
   * it will not give all objects with the given prefix (to arbitrary level of nesting),
   * but only the top level files in the folder given by the prefix and folders in that directory (if prefix ends on the delimiter),
   * or only the directory itself (if prefix does not end on the delimiter).
   * Thus if we want arbitrary deep files that happen to be nested within the path as defined by the prefix,
   * we need to leave the delimiter undefined.
   * @param prefix
   * @return
   */
  private def listFilesWithPrefix(prefix: String): Seq[String] = {
    val objectPrefix = s"$dirPathNormalized$prefix".stripSuffix(delimiter) + delimiter
    val blobs: Page[Blob] = storage.list(
      bucketName,
      Storage.BlobListOption.prefix(objectPrefix)
    )
    blobs.iterateAll().iterator().asScala.toSeq.map(x => x.getName)
  }

  override def copyDirectory(dirPath: String, toDirPath: String): Unit = {
    try {
      val fromDirPrefix = s"$dirPathNormalized$dirPath".stripSuffix(delimiter)
      val fromDirTopLevelFolder = fromDirPrefix.split(delimiter).last.stripSuffix(delimiter)
      val containedFiles = listFilesWithPrefix(dirPath)
      logger.debug(s"Files to be copied: ${containedFiles}")
      val newDirPrefix = s"$dirPathNormalized${toDirPath.stripSuffix(delimiter)}/$fromDirTopLevelFolder"
      containedFiles.foreach(file => {
        if (!file.endsWith("/")) {
          val newFile = file.replace(fromDirPrefix, newDirPrefix)
          logger.debug(s"trying to copy '$file' to '$newFile' for bucket '$bucketName'")
          val sourceBlobId: BlobId = BlobId.of(bucketName, file)
          val targetBlobId: BlobId = BlobId.of(bucketName, newFile)
          storage.copy(CopyRequest.of(sourceBlobId, targetBlobId))
        }
      })
    }
    catch {
      case e: StorageException =>
        logger.error(s"GCS copy operation from path '$dirPath' to path '$toDirPath' failed.")
        throw e
    }
  }

  override def deleteDirectory(dirPath: String): Unit = {
    try {
      val files = listFilesWithPrefix(dirPath)
      files.foreach(file => {
        val blobId = BlobId.of(bucketName, file)
        storage.get(blobId).delete()
      })
    }
    catch {
      case e: StorageException =>
        logger.error("GCS failed to process delete request", e)
        throw e
    }

  }

  override def moveDirectory(dirPath: String, toDirPath: String): Unit = {
    copyDirectory(dirPath, toDirPath)
    deleteDirectory(dirPath)
  }
}
