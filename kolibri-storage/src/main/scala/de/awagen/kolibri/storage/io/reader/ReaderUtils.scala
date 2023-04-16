package de.awagen.kolibri.storage.io.reader

import org.slf4j.{Logger, LoggerFactory}

object ReaderUtils {

  private[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Given some base path and delimiter, normalize such that it doesnt start with delimiter but ends with it
   *
   * @param dirPath   - the base bucket path
   * @param delimiter - the chosen delimiter
   * @return - normalized path
   */
  def normalizeBucketPath(dirPath: String, delimiter: String): String = {
    dirPath.trim.stripPrefix(delimiter).stripSuffix(delimiter).trim match {
      case "" => ""
      case path => s"$path$delimiter"
    }
  }

  /**
   * Given a normalized dirPath (either empty or starts without delimiter and ends with delimiter)
   * a passed fileIdentifier and delimiter, check if identifier starts with
   * dirPath, otherwise prepend it to gain full path
   *
   * @param dirPath        - the normalized base path (does NOT start with delimier but ends with it OR is empty (e.g also not containing delimiter))
   * @param fileIdentifier - identifier (for buckets usually a dir-like path)
   * @param delimiter      - the delimiter used (usually "/")
   * @return
   */
  def getFullBucketPath(dirPath: String, fileIdentifier: String, delimiter: String): String = {
    if (dirPath.nonEmpty && fileIdentifier.startsWith(dirPath)) fileIdentifier
    else if (dirPath.isEmpty) fileIdentifier.stripPrefix(delimiter)
    else s"$dirPath${fileIdentifier.stripPrefix(delimiter)}"
  }

  def safeContentLinesRead(contentReader: Reader[String, Seq[String]],
                           path: String,
                           default: Iterator[String],
                           logOnFail: Boolean): Iterator[String] = {
    try {
      contentReader.getSource(path).getLines()
    }
    catch {
      case e: Exception =>
        if (logOnFail) logger.warn(s"could not load content from path $path", e)
        default
    }
  }

  def safeContentRead(contentReader: Reader[String, Seq[String]],
                      path: String,
                      default: String,
                      logOnFail: Boolean): String = {
    safeContentLinesRead(contentReader, path, Seq(default).iterator, logOnFail).mkString("\n")
  }

}
