package de.awagen.kolibri.storage.io.reader

import scala.io.Source

case class LocalResourceFileReader(basePath: String,
                                   delimiterAndPosition: Option[(String, Int)],
                                   fromClassPath: Boolean,
                                   encoding: String = "UTF-8") extends Reader[String, Seq[String]] {

  val normedBasePath: String = basePath.strip().stripSuffix("/")
  val pathSeparator: String = if (fromClassPath && normedBasePath.isEmpty) "" else "/"

  /**
   * transform relative fileIdentifier to full path
   *
   * @param fileIdentifier
   * @return
   */
  def fileIdentifierToFullPath(fileIdentifier: String): String = {
    if (fileIdentifier.startsWith(normedBasePath)) fileIdentifier
    else s"$normedBasePath$pathSeparator${fileIdentifier.stripPrefix("/")}"
  }

  /**
   * Get source for the passed fileIdentifier
   *
   * @param fileIdentifier - path relative to the configured LOCAL_RESOURCES_READ_DIR
   * @return - Source corresponding to the file
   */
  override def getSource(fileIdentifier: String): Source = {
    val fullFileIdentifier = fileIdentifierToFullPath(fileIdentifier)
    if (fromClassPath) FileReaderUtils.localResourceSource(fullFileIdentifier, encoding)
    else FileReaderUtils.localSource(fullFileIdentifier, encoding)
  }

  /**
   * if delimiterAndPosition is set, split each line by the delimiter and select the nth element,
   * otherwise just return trimmed lines
   *
   * @param fileIdentifier - file path relative to configured LOCAL_RESOURCES_READ_DIR
   * @return - Seq of lines of file
   */
  override def read(fileIdentifier: String): Seq[String] = {
    val source: Source = getSource(fileIdentifier)
    delimiterAndPosition.fold(FileReaderUtils.trimmedEntriesByLineFromFile(source))(
      x => FileReaderUtils.pickUniquePositionPerLineDeterminedByDelimiter(source, x._1, x._2)
    )
  }
}
