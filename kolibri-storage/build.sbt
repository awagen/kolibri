import sbt.Keys._
import sbt.url

val sl4jApiVersion = "1.7.30"
val scalaTestVersion = "3.2.2"
val playVersion = "2.9.1"
val playLogbackVersion = "2.8.2"
val sprayVersion = "1.3.5"
val awsSdkVersion = "1.11.713"
val googleCloudStorageClientVersion = "2.1.1"
val testcontainersVersion = "1.16.3"
val apacheCommonsIOVersion = "2.8.0"

// scoverage plugin setting to exclude classes from coverage report
coverageExcludedPackages := ""

test in assembly := {} //causes no tests to be executed when calling "sbt assembly" (without this setting executes all)
assemblyJarName in assembly := s"kolibri-storage.${version.value}.jar" //jar name
//sbt-assembly settings. If it should only hold for specific subproject build, place the 'assemblyMergeStrategy in assembly' within subproject settings
assemblyMergeStrategy in assembly := {
  case x if x.endsWith("logback.xml") =>
    MergeStrategy.last
  case "module-info.class" => MergeStrategy.discard
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case manifest if manifest.contains("MANIFEST.MF") =>
    // We don't need manifest files since sbt-assembly will create
    // one with the given settings
    MergeStrategy.discard
  case referenceOverrides if referenceOverrides.contains("reference-overrides.conf") =>
    // Keep the content for all reference-overrides.conf files
    MergeStrategy.concat
  case reference if reference.contains("reference.conf") =>
    // Keep the content for all reference.conf files
    MergeStrategy.concat
  case x =>
    // For all the other files, use the default sbt-assembly merge strategy
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

name := "kolibri-storage"
libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % sl4jApiVersion,
  //scala test framework (http://www.scalatest.org/install)
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
  // include scala reflection, e.g TypeTags and such
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  // json parsing
  "com.typesafe.play" %% "play-json" % playVersion,
  // logback
  "com.typesafe.play" %% "play-logback" % playLogbackVersion,
  "io.spray" %% "spray-json" % sprayVersion,
  "com.amazonaws" % "aws-java-sdk" % awsSdkVersion,
  "com.google.cloud" % "google-cloud-storage" % googleCloudStorageClientVersion,
  "commons-io" % "commons-io" % apacheCommonsIOVersion,
  "org.testcontainers" % "testcontainers" % testcontainersVersion % Test,
  "org.testcontainers" % "localstack" % testcontainersVersion % Test
)

libraryDependencies := { libraryDependencies.value :+ ("de.awagen.kolibri" %% "kolibri-datatypes" % version.value) }

// ---- start settings for publishing to mvn central
// (needs to fully be in build.sbt of sub-project, also the non-project-specific parts)
organization := "de.awagen.kolibri"
organizationName := "awagen"
organizationHomepage := Some(url("http://awagen.de"))
developers := List(
  Developer(
    id    = "awagen",
    name  = "Andreas Wagenmann",
    email = "awagen@posteo.net",
    url   = url("https://github.com/awagen")
  )
)
licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
pomIncludeRepository := { _ => false } // Remove all additional repository other than Maven Central from POM
publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
publishMavenStyle := true

scmInfo := Some(
  ScmInfo(
    url("https://github.com/awagen/kolibri"),
    "scm:git@github.com:awagen/kolibri.git"
  )
)
description := "kolibri-storage provides the implementations regarding storage (e.g local file system, classpath access," +
  " cloud storage such as s3, gcs and the like."
homepage := Some(url("https://github.com/awagen/kolibri"))
// ---- end settings for publishing to mvn central