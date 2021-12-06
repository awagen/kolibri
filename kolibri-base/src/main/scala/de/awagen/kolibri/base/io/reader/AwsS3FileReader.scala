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
import com.amazonaws.services.s3.model.{GetObjectRequest, S3Object, S3ObjectInputStream}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import de.awagen.kolibri.base.io.reader.ReaderUtils.normalizeBucketPath

import java.util.Objects
import scala.io.Source


case class AwsS3FileReader(bucketName: String,
                           dirPath: String,
                           region: Regions,
                           delimiter: String = "/") extends Reader[String, Seq[String]] {

  val dirPathNormalized: String = normalizeBucketPath(dirPath, delimiter)

  private[this] var s3Client: AmazonS3 = _

  // workaround for serialization
  def setS3ClientIfNotSet(): Unit = {
    synchronized {
      if (Objects.isNull(s3Client)) {
        s3Client = AmazonS3ClientBuilder.standard().withRegion(region).build()
      }
    }
  }

  override def getSource(fileIdentifier: String): Source = {
    setS3ClientIfNotSet()
    val normalizedFileIdentifier = fileIdentifier.trim match {
      case identifier if dirPathNormalized.nonEmpty && identifier.startsWith(dirPathNormalized) => identifier
      case identifier => s"$dirPath$identifier".stripSuffix(delimiter)
    }
    val obj: S3Object = s3Client.getObject(new GetObjectRequest(bucketName, normalizedFileIdentifier))
    val objData: S3ObjectInputStream = obj.getObjectContent;
    Source.fromInputStream(objData)
  }

  override def read(fileIdentifier: String): Seq[String] = {
    setS3ClientIfNotSet()
    val source: Source = getSource(fileIdentifier)
    source.getLines().toSeq
  }
}
