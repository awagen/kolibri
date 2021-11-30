
lazy val commonSettings = Seq(
  // with TrackIfMissing, sbt will not try to compile internal
  // (inter-project) dependencies automatically if there are *.class files
  // (or JAR file when exportJars is true) in output directory
  trackInternalDependencies := TrackLevel.TrackIfMissing
)

lazy val root = (project in file("."))
  .aggregate(`kolibri-datatypes`, `kolibri-base`)
  .settings(update / aggregate := false)

lazy val `kolibri-datatypes` = (project in file("kolibri-datatypes"))
  .settings(
    commonSettings
  )
lazy val `kolibri-base` = (project in file("kolibri-base"))
  .settings(
    commonSettings
  )
  .dependsOn(`kolibri-datatypes` % "compile->compile")



