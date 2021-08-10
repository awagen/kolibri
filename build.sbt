import sbt.Keys._

val sl4jApiVersion = "1.7.30"
val scalaTestVersion = "3.2.2"
val playVersion = "2.9.1"
val playLogbackVersion = "2.8.2"
val sprayVersion = "1.3.5"

ThisBuild / scalaVersion := "2.13.2"
ThisBuild / version := "0.1.0-alpha5"

lazy val jvmOptions = Seq(
  "-Xms1G",
  "-Xmx4G",
  "-Xss1M",
  "-XX:+CMSClassUnloadingEnabled",
  "-XX:MaxPermSize=256M"
)

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

// Scala Compiler Options
ThisBuild / scalacOptions ++= Seq(
  //"-target:jvm-8",
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  //  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  //  "-Ywarn-inaccessible",
  "-Ywarn-dead-code",
  //  "-Xfatal-warnings" //turn warnings into errors
)
//javaOptions
ThisBuild / javaOptions in Runtime ++= jvmOptions
ThisBuild / javaOptions in Test ++= jvmOptions

//javacOptions
ThisBuild / javacOptions ++= Seq(
  "-source", "1.13",
  "-target", "1.13"
)

//scalacOptions
ThisBuild / scalacOptions ++= Seq(
  "-encoding", "utf8", // Option and arguments on same line
  "-language:postfixOps" // New lines for each options
)

//by default tasks run in same JVM as sbt. This might lead to crashing, thus we fork the JVM
ThisBuild / fork in Runtime := true
ThisBuild / fork in Test := true
ThisBuild / fork in run := true

//logging
//disables buffered logging (buffering would cause results of tests to be logged only at end of all tests)
//http://www.scalatest.org/user_guide/using_scalatest_with_sbt
ThisBuild / logBuffered in Test := false
//disable version conflict messages
ThisBuild / evictionWarningOptions in update := EvictionWarningOptions.default
  .withWarnTransitiveEvictions(false)
  .withWarnDirectEvictions(false)

lazy val `kolibri-datatypes` = (project in file("."))
  .settings(
    name := "kolibri-datatypes",
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
  )
  .enablePlugins(JvmPlugin)

// settings for publishing
ThisBuild / organization := "de.awagen.kolibri"
ThisBuild / organizationName := "awagen"
ThisBuild / organizationHomepage := Some(url("http://awagen.de"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/awagen/kolibri-datatypes"),
    "scm:git@github.com:awagen/kolibri-datatypes.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "awagen",
    name  = "Andreas Wagenmann",
    email = "awagen@posteo.net",
    url   = url("https://github.com/awagen")
  )
)

ThisBuild / description := "kolibri-datatypes provides the datatypes used within the kolibri project. Kolibri provides a clusterable job execution framework based on Akka."
ThisBuild / licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("https://github.com/awagen/kolibri-datatypes"))

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true