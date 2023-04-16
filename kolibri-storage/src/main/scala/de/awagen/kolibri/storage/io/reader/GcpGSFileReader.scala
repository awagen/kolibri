package de.awagen.kolibri.storage.io.reader

import com.google.cloud.storage.{Blob, BlobId, Storage, StorageOptions}
import de.awagen.kolibri.storage.io.reader.ReaderUtils.{getFullBucketPath, normalizeBucketPath}

import scala.io.Source

/**
 * File reader reading data from gcp bucket
 *
 * @param bucketName - name of the bucket
 * @param dirPath    - path within the bucket relative to which the data is read
 * @param projectID  - project id the bucket belongs to
 */
case class GcpGSFileReader(bucketName: String,
                           dirPath: String,
                           projectID: String,
                           delimiter: String = "/") extends Reader[String, Seq[String]] {

  val dirPathNormalized: String = normalizeBucketPath(dirPath, delimiter)

  val storage: Storage = StorageOptions.newBuilder
    .setProjectId(projectID).build.getService

  private[reader] def getBlob(objectName: String): Blob = {
    storage.get(BlobId.of(bucketName, objectName))
  }

  private[this] def getBlobPath(fileIdentifier: String): String = {
    getFullBucketPath(dirPathNormalized, fileIdentifier, delimiter)
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
