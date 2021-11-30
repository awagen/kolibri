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

import com.google.cloud.storage.Blob

import scala.io.Source
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions


/**
  * File reader reading data from gcp bucket
  *
  * @param bucketName - name of the bucket
  * @param dirPath    - path within the bucket relative to which the data is read
  * @param projectID  - project id the bucket belongs to
  */
case class GcpGSFileReader(bucketName: String,
                           dirPath: String,
                           projectID: String) extends Reader[String, Seq[String]] {

  val dirPathNormalized: String = dirPath.stripPrefix("/").stripSuffix("/")

  val storage: Storage = StorageOptions.newBuilder
    .setProjectId(projectID).build.getService

  private[reader] def getBlob(objectName: String): Blob = {
    storage.get(BlobId.of(bucketName, objectName))
  }

  private[this] def getBlobPath(fileIdentifier: String): String = {
    s"$dirPathNormalized/${fileIdentifier.stripPrefix("/")}"
  }

  override def getSource(fileIdentifier: String): Source = {
    val blobPath: String = getBlobPath(fileIdentifier)
    val blob: Blob = getBlob(blobPath)
    Source.fromBytes(blob.getContent(), "UTF-8")
  }

  override def read(fileIdentifier: String): Seq[String] = {
    val blobPath: String = getBlobPath(fileIdentifier)
    val blob: Blob = getBlob(blobPath)
    Source.fromBytes(blob.getContent(), "UTF-8").getLines().toSeq
  }
}
