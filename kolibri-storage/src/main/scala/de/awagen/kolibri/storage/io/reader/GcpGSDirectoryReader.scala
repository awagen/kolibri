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

import com.google.api.gax.paging.Page
import com.google.cloud.storage.{Blob, Storage, StorageOptions}
import de.awagen.kolibri.storage.io.reader.GcpGSDirectoryReader.logger
import de.awagen.kolibri.storage.io.reader.ReaderUtils.normalizeBucketPath
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.IteratorHasAsScala

object GcpGSDirectoryReader {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

}


/**
 * Directory reader providing listing of bucket contents in provided path, assuming a file system
 * analogue using the provided delimiter as directory path separator
 *
 * @param bucketName - name of the bucket
 * @param dirPath    - path within the bucket relative to which the data is read
 * @param projectID  - project id the bucket belongs to
 * @param delimiter  - delimiter that determines the "folder structure" from the object names in the bucket
 * @param fileFilter - filter on the filename, e.g only objects will be listed for which the filter yields true
 */
case class GcpGSDirectoryReader(bucketName: String,
                                dirPath: String,
                                projectID: String,
                                delimiter: String = "/",
                                fileFilter: String => Boolean) extends DataOverviewReader {
  val dirPathNormalized: String = normalizeBucketPath(dirPath, delimiter)
  val storage: Storage = StorageOptions.newBuilder.setProjectId(projectID).build.getService

  override def listResources(subDir: String, baseFilenameFilter: String => Boolean): Seq[String] = {
    var prefix = s"$dirPathNormalized${subDir.stripPrefix(delimiter).stripSuffix(delimiter)}$delimiter"
    if (prefix == delimiter) prefix = ""
    logger.debug(s"looking for resources in bucket '$bucketName' and prefix '$prefix'")
    val blobs: Page[Blob] = storage.list(
      bucketName,
      // prefix determines the path prefix of the "filename"
      Storage.BlobListOption.prefix(prefix),
      // delimiter specifies the path delimiter
      Storage.BlobListOption.delimiter(delimiter)
    )
    val foundFiles = blobs.iterateAll().iterator().asScala.toSeq
      // blob name contains the full path from bucket root
      .map(x => x.getName)
      .filter(x => fileFilter.apply(x))
      .filter(x => baseFilenameFilter.apply(x))
    logger.debug(s"found files in bucket '$bucketName' and prefix '$prefix': ${foundFiles.mkString(",")}")
    foundFiles
  }
}
