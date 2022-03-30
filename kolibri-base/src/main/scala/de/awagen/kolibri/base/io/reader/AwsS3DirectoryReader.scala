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

import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.{ListObjectsV2Request, S3ObjectSummary}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import de.awagen.kolibri.base.io.reader.ReaderUtils.normalizeBucketPath
import org.slf4j.{Logger, LoggerFactory}

import java.util.Objects
import scala.jdk.CollectionConverters._


case class AwsS3DirectoryReader(bucketName: String,
                                dirPath: String,
                                region: Regions,
                                delimiter: String = "/",
                                fileFilter: String => Boolean,
                                awsS3Client: Option[AmazonS3] = None) extends DataOverviewReader {

  val dirPathNormalized: String = normalizeBucketPath(dirPath, delimiter)

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private[this] var s3Client: AmazonS3 = awsS3Client.orNull

  // workaround for serialization
  def setS3ClientIfNotSet(): Unit = {
    synchronized {
      if (Objects.isNull(s3Client)) {
        s3Client = AmazonS3ClientBuilder.standard().withRegion(region).build()
      }
    }
  }

  private[reader] def applyPathFilter(baseFilenameFilter: String => Boolean): String => Boolean = {
    x => {
      val fileName = x.split(delimiter).map(x => x.trim).filter(x => x.nonEmpty).last
      fileFilter.apply(fileName) && baseFilenameFilter.apply(fileName)
    }
  }

  override def listResources(subDir: String, baseFilenameFilter: String => Boolean): Seq[String] = {
    try {
      setS3ClientIfNotSet()
      var fullprefix = s"$dirPathNormalized${subDir.stripPrefix(delimiter)}".stripPrefix(delimiter).stripSuffix(delimiter) + delimiter
      if (fullprefix == delimiter) fullprefix = ""
      // behavior of the listObjectsV2Request: using no delimiter here will cause no common prefixes to be found,
      // yet all paths containing the prefix will be listed in object summaries (whether directory or file; all hierarchies
      // up if containing the prefix).
      // If delimiter is defined, only the level directly after the prefix (should end with delimiter) will be
      // contained within common prefixes but no object fill be found if there is no file on that level.
      // Exception to above: if delimiter defined and path ending on delimiter passed, will return the folder matching
      // exactly this path as object, thus the below filtering to avoid retrieving this.
      val req = new ListObjectsV2Request()
        .withBucketName(bucketName)
        .withPrefix(fullprefix)
        .withDelimiter(delimiter)
      val listing = s3Client.listObjectsV2(req)
      // listing contains files as objects
      val files: Seq[String] = listing.getObjectSummaries.asScala.toSeq
        .map(x => x.getKey)
        .filter(x => !(x.endsWith(delimiter) && fullprefix.equals(x)))
      // listing contains directories as common prefixes (ending with delimiter set in the request, if any)
      val folders: Seq[String] = listing.getCommonPrefixes.asScala.toSeq
        .filter(x => !fullprefix.equals(x))
      val paths: Seq[String] = files
        .concat(folders)
        .filter(x => applyPathFilter(baseFilenameFilter).apply(x))
      paths
    }
    catch {
      case e: Throwable =>
        logger.error("error reading s3", e)
        Seq.empty
    }
  }
}
