ThisBuild / scalaVersion := "2.13.2"

ThisBuild / version := "0.1.5"

// Scala Compiler Options
ThisBuild / scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-dead-code",
  "-language:postfixOps" // New lines for each options
)
//javacOptions
ThisBuild / javacOptions ++= Seq(
  "-source", "11",
  "-target", "11"
)

//by default run types run on same JVM as sbt. This might lead to crashing, thus we fork the JVM.
ThisBuild / fork in Runtime := true
ThisBuild / fork in Test := true
ThisBuild / fork in IntegrationTest := true
ThisBuild / fork in run := true

// with TrackIfMissing, sbt will not try to compile internal
// (inter-project) dependencies automatically if there are *.class files
// (or JAR file when exportJars is true) in output directory
ThisBuild / trackInternalDependencies := TrackLevel.TrackIfMissing

//logging
//disables buffered logging (buffering would cause results of tests to be logged only at end of all tests)
//http://www.scalatest.org/user_guide/using_scalatest_with_sbt
ThisBuild / logBuffered in Test := false
ThisBuild / logBuffered in IntegrationTest := false
//disable version conflict messages
ThisBuild / evictionWarningOptions in update := EvictionWarningOptions.default
  .withWarnTransitiveEvictions(false)
  .withWarnDirectEvictions(false)

// actual project definitions
// NOTE: do not additionally define the project definitions per single-project build.sbt file,
// otherwise changes from projects referenced in dependsOn here dont seem to be picked up from local
// but need local jar publishing
lazy val root = (project in file("."))
  .aggregate(`kolibri-datatypes`, `kolibri-storage`, `kolibri-definitions`, `kolibri-fleet-akka`, `kolibri-fleet-zio`)
  .settings(update / aggregate := false)
lazy val `kolibri-datatypes` = (project in file("kolibri-datatypes"))
  .enablePlugins(JvmPlugin)
lazy val `kolibri-storage` = (project in file("kolibri-storage"))
  .dependsOn(`kolibri-datatypes` % "compile->compile")
  .enablePlugins(JvmPlugin)
lazy val `kolibri-definitions` = (project in file("kolibri-definitions"))
  // storage already includes datatypes, thus only adding storage
  // dependency here
  .dependsOn(`kolibri-storage` % "compile->compile")
  .enablePlugins(JvmPlugin)
  // extending Test config here to have access to test classpath
  .configs(IntegrationTest.extend(Test))
  .settings(
    Defaults.itSettings
  )
lazy val `kolibri-fleet-akka` = (project in file("kolibri-fleet-akka"))
  .dependsOn(`kolibri-definitions` % "compile->compile")
  .enablePlugins(JvmPlugin)
  // extending Test config here to have access to test classpath
  .configs(IntegrationTest.extend(Test))
  .settings(
    Defaults.itSettings
  )
lazy val `kolibri-fleet-zio` = (project in file("kolibri-fleet-zio"))
  .dependsOn(`kolibri-definitions` % "compile->compile")
  .enablePlugins(JvmPlugin)
  // extending Test config here to have access to test classpath
  .configs(IntegrationTest.extend(Test))
  .settings(
    Defaults.itSettings
  )
