import sbt.Keys._
import sbt.url

val sl4jApiVersion = "1.7.30"
val scalaTestVersion = "3.2.2"
val scalaMockVersion = "5.1.0"
val shapelessVersion = "2.3.3"
val logbackVersion = "1.2.3"
val macwireVersion = "2.4.0"
val scalacScoverageRuntimeVersion = "1.4.9"
val testcontainersVersion = "1.16.3"
val sprayVersion = "1.3.6"

// scoverage plugin setting to exclude classes from coverage report
coverageExcludedPackages := "de\\.awagen\\.kolibri\\.base\\.config\\..*;" +
  "de\\.awagen\\.kolibri\\.base\\.processing\\.ProcessingMessages;" +
  "de\\.awagen\\.kolibri\\.base\\.processing\\.JobPartIdentifiers;" +
  "de\\.awagen\\.kolibri\\.base\\.exceptions\\..*;" +
  "de\\.awagen\\.kolibri\\.base\\.traits\\.Traits;" +
  "de\\.awagen\\.kolibri\\.base\\.usecase\\.searchopt\\.domain\\..*;" +
  "de\\.awagen\\.kolibri\\.base\\.usecase\\.searchopt\\.parse\\.ParsingConfig;"

// defining fixed env vars for test scope
envVars in Test := Map("PROFILE" -> "test")
envVars in IntegrationTest := Map("PROFILE" -> "test")

test in assembly := {} //causes no tests to be executed when calling "sbt assembly" (without this setting executes all)
assemblyJarName in assembly := s"kolibri-base.${version.value}.jar" //jar name
//sbt-assembly settings. If it should only hold for specific subproject build, place the 'assemblyMergeStrategy in assembly' within subproject settings
assemblyMergeStrategy in assembly := {
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
  case x if x.endsWith(".proto") =>
    MergeStrategy.first
  // same class conflicts (too general but resolving for now)
  case x if x.endsWith(".class") =>
    MergeStrategy.first
  case x =>
    // For all the other files, use the default sbt-assembly merge strategy
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

val additionalResolvers = Seq(
  ("scalaz-bintray" at "https://dl.bintray.com/scalaz/releases").withAllowInsecureProtocol(false),
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

val additionalDependencies = Seq(
  "org.slf4j" % "slf4j-api" % sl4jApiVersion,
  //scala test framework (scalactic is recommended but not required)(http://www.scalatest.org/install)
  "org.scalactic" %% "scalactic" % scalaTestVersion % "test,it",
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test,it",
  "io.spray" %%  "spray-json" % sprayVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "com.chuusai" %% "shapeless" % shapelessVersion,
  "org.slf4j" % "slf4j-api" % sl4jApiVersion,
  "com.softwaremill.macwire" %% "macros" % macwireVersion,
  "com.softwaremill.macwire" %% "util" % macwireVersion,
  "org.scalamock" %% "scalamock" % scalaMockVersion % Test,
  "org.scoverage" %% "scalac-scoverage-runtime" % scalacScoverageRuntimeVersion % Test,
  "org.testcontainers" % "testcontainers" % testcontainersVersion % Test,
  "org.testcontainers" % "localstack" % testcontainersVersion % Test
)

name := "kolibri-base"
libraryDependencies ++= additionalDependencies
// the syntax for adding the datatypes dependency is needed since version.value is not accessible in above dependency seq
// definition due to "`value` can only be used within a task or setting macro, such as :=, +=, ++=, Def.task, or Def.setting."
libraryDependencies := {
  libraryDependencies.value :+ ("de.awagen.kolibri" %% "kolibri-storage" % version.value)
}
resolvers ++= additionalResolvers

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
description := "kolibri-base provides shared abstractions for the definition of computation for the the kolibri project. " +
  "Kolibri provides a job execution framework."
homepage := Some(url("https://github.com/awagen/kolibri"))
// ---- end settings for publishing to mvn central
