import sbt.Keys._
import sbt.url

val sl4jApiVersion = "1.7.30"
val scalaTestVersion = "3.2.2"
val playVersion = "2.9.1"
val sprayVersion = "1.3.5"
val zioVersion = "2.0.13"
val zioJsonVersion = "0.5.0"
val zioCacheVersion = "0.2.3"
val zioConfigVersion = "4.0.0-RC14"
val zioLoggingVersion = "2.1.12"
val zioHttpVersion = "3.0.0-RC1"
val mockitoVersion = "3.2.10.0"

// scoverage plugin setting to exclude classes from coverage report
coverageExcludedPackages := ""

test in assembly := {} //causes no tests to be executed when calling "sbt assembly" (without this setting executes all)
assemblyJarName in assembly := s"kolibri-fleet-zio.${version.value}.jar" //jar name
//sbt-assembly settings. If it should only hold for specific subproject build, place the 'assemblyMergeStrategy in assembly' within subproject settings
assemblyMergeStrategy in assembly := {
  // picking last to make sure the application configs and logback config of kolibri-fleet-zio are picked up instead
  // from a dependency
  case x if "application.*\\.conf".r.findFirstMatchIn(x.split("/").last).nonEmpty =>
    MergeStrategy.last
  case x if x.endsWith("logback.xml") && !x.contains("kolibri-fleet-zio") =>
    MergeStrategy.discard
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

compile / logLevel := Level.Error
test / logLevel := Level.Error

name := "kolibri-fleet-zio"
libraryDependencies ++= Seq(
  //scala test framework (http://www.scalatest.org/install)
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
  // include scala reflection, e.g TypeTags and such
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  // json parsing
  "com.typesafe.play" %% "play-json" % playVersion,
  "io.spray" %% "spray-json" % sprayVersion,
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-streams" % zioVersion,
  "dev.zio" %% "zio-http" % zioHttpVersion,
  "dev.zio" %% "zio-json" % zioJsonVersion,
  "dev.zio" %% "zio-cache" % zioCacheVersion,
  "dev.zio" %% "zio-config" % zioConfigVersion,
  "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
  "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
  "dev.zio" %% "zio-logging-slf4j" % zioLoggingVersion,
  "dev.zio" %% "zio-http" % zioHttpVersion,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  "dev.zio" %% "zio-test-magnolia" % zioVersion % Test,
  "org.scalatestplus" %% "mockito-3-4" % mockitoVersion % Test
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

libraryDependencies := {
  libraryDependencies.value :+ ("de.awagen.kolibri" %% "kolibri-definitions" % version.value)
}

// ---- start settings for publishing to mvn central
// (needs to fully be in build.sbt of sub-project, also the non-project-specific parts)
organization := "de.awagen.kolibri"
organizationName := "awagen"
organizationHomepage := Some(url("http://awagen.de"))
developers := List(
  Developer(
    id = "awagen",
    name = "Andreas Wagenmann",
    email = "awagen@posteo.net",
    url = url("https://github.com/awagen")
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
description := "kolibri-fleet-zio provides the job execution engine based on zio. It is based on a loose node" +
  " coupling in the sense that the nodes synchronize which one picks which job to execute. The simplest variant" +
  " of this sync mechanism only requires storage, rather than particular db or queue setup."
homepage := Some(url("https://github.com/awagen/kolibri"))
// ---- end settings for publishing to mvn central