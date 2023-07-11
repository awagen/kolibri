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

import com.google.cloud.storage.{BlobId, BlobInfo, Storage, StorageOptions}
import de.awagen.kolibri.storage.io.reader.ReaderUtils.normalizeBucketPath
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter

/**
  * A GCP file writer writing data to google cloud storage
  * @param bucketName - name of bucket to write to
  * @param dirPath - the path within the bucket to write to, mimicking a folder structure
  * @param projectID - the project id for which the bucket was created
  */
case class GcpGSFileWriter(bucketName: String,
                           dirPath: String,
                           projectID: String,
                           delimiter: String = "/") extends FileWriter[String, Unit] {
  val dirPathNormalized: String = normalizeBucketPath(dirPath, delimiter)

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

  // TODO: implement
  override def delete(targetIdentifier: String): Either[Exception, Unit] = ???

  // TODO: implement
  override def copyDirectory(dirPath: String, toDirPath: String): Unit = ???

  // TODO: implement
  override def moveDirectory(dirPath: String, toDirPath: String): Unit = ???

  // TODO: implement
  override def deleteDirectory(dirPath: String): Unit = ???
}
