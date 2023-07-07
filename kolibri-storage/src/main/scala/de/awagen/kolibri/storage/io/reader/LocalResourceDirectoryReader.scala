package de.awagen.kolibri.storage.io.reader

import scala.io.Source

case class LocalResourceDirectoryReader(baseDir: String,
                                        baseFilenameFilter: String => Boolean = _ => true,
                                        encoding: String = "UTF-8") extends DataOverviewReader {
  val normedBaseDir: String = baseDir.stripSuffix("/")

  /**
   * List files in subDir of baseDir that pass the given filenameFilter. Returns full paths of matching files
   *
   * @param subDir         - the sub-directory within the baseDir to look for files
   * @param filenameFilter - the filter for the filenames
   * @return Seq of full paths of files within the sub-directory within the baseDir matching the given filter
   */
  override def listResources(subDir: String, filenameFilter: String => Boolean = _ => true): Seq[String] = {
    val fullDir = s"$normedBaseDir/$subDir".stripSuffix("/").stripPrefix("/")
    Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(fullDir), encoding)
      .getLines()
      .map(x => s"$fullDir/$x".stripSuffix("/"))
      .filter(baseFilenameFilter.apply)
      .filter(filenameFilter.apply)
      .toSeq
  }

}
