package de.awagen.kolibri.storage.io.reader

import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.{GetObjectRequest, S3Object, S3ObjectInputStream}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import de.awagen.kolibri.storage.io.reader.ReaderUtils.normalizeBucketPath

import java.util.Objects
import scala.io.Source

case class AwsS3FileReader(bucketName: String,
                           dirPath: String,
                           region: Regions,
                           delimiter: String = "/",
                           awsS3Client: Option[AmazonS3] = None) extends Reader[String, Seq[String]] {

  val dirPathNormalized: String = normalizeBucketPath(dirPath, delimiter)

  private[this] var s3Client: AmazonS3 = awsS3Client.orNull

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
      case identifier => s"$dirPathNormalized$identifier".stripSuffix(delimiter)
    }
    val obj: S3Object = s3Client.getObject(new GetObjectRequest(bucketName, normalizedFileIdentifier))
    val objData: S3ObjectInputStream = obj.getObjectContent
    Source.fromInputStream(objData)
  }

  override def read(fileIdentifier: String): Seq[String] = {
    setS3ClientIfNotSet()
    val source: Source = getSource(fileIdentifier)
    source.getLines().toSeq
  }
}
