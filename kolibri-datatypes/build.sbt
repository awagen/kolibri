import sbt.Keys._

val sl4jApiVersion = "1.7.30"
val scalaTestVersion = "3.2.2"
val playVersion = "2.9.1"
val playLogbackVersion = "2.8.2"
val sprayVersion = "1.3.5"

version := "0.1.0-rc1"

// scoverage plugin setting to exclude classes from coverage report
coverageExcludedPackages := ""

test in assembly := {} //causes no tests to be executed when calling "sbt assembly" (without this setting executes all)
assemblyJarName in assembly := s"kolibri-datatypes.${version.value}.jar" //jar name
//sbt-assembly settings. If it should only hold for specific subproject build, place the 'assemblyMergeStrategy in assembly' within subproject settings
assemblyMergeStrategy in assembly := {
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

name := "kolibri-datatypes"
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
  "io.spray" %% "spray-json" % sprayVersion
)

// ---- start settings for publishing to mvn central
scmInfo := Some(
  ScmInfo(
    url("https://github.com/awagen/kolibri-datatypes"),
    "scm:git@github.com:awagen/kolibri-datatypes.git"
  )
)
description := "kolibri-datatypes provides the datatypes used within the kolibri project. Kolibri provides a clusterable job execution framework based on Akka."
homepage := Some(url("https://github.com/awagen/kolibri-datatypes"))
// ---- end settings for publishing to mvn central