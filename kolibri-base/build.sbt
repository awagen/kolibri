import sbt.Keys._
import sbt.url

val sl4jApiVersion = "1.7.30"
val scalaTestVersion = "3.2.2"
val scalaMockVersion = "5.1.0"
val kolibriDatatypesVersion = "0.1.0-rc1"
val akkaVersion = "2.6.14"
val akkaContribVersion = "2.5.31"
val akkaHttpVersion = "10.2.1"
val akkaManagementVersion = "1.0.8"
val shapelessVersion = "2.3.3"
val logbackVersion = "1.2.3"
val kryoSerializationVersion = "2.2.0"
val awsSdkVersion = "1.11.713"
val googleCloudStorageClientVersion = "2.1.1"
val apacheCommonsIOVersion = "2.8.0"
val kamonVersion = "2.2.0"
val macwireVersion = "2.4.0"
val scalacScoverageRuntimeVersion = "1.4.9"

version := "0.1.0-rc2"

lazy val jvmOptions = Seq(
  "-Xms1G",
  "-Xmx4G",
  "-Xss1M",
  "-XX:+CMSClassUnloadingEnabled"
)
// set javaOptions
javaOptions in Runtime ++= jvmOptions
javaOptions in Test ++= jvmOptions

// scoverage plugin setting to exclude classes from coverage report
coverageExcludedPackages := "de\\.awagen\\.kolibri\\.base\\.config\\..*;" +
  ".*\\.ClusterNode;" +
  ".*\\.ClusterStates;" +
  "de\\.awagen\\.kolibri\\.base\\.actors\\.flows\\.FlowAttributes;" +
  "de\\.awagen\\.kolibri\\.base\\.actors\\.work\\.worker\\.ProcessingMessages;" +
  "de\\.awagen\\.kolibri\\.base\\.actors\\.work\\.worker\\.JobPartIdentifiers;" +
  "de\\.awagen\\.kolibri\\.base\\.exceptions\\..*;" +
  "de\\.awagen\\.kolibri\\.base\\.traits\\.Traits;" +
  "de\\.awagen\\.kolibri\\.base\\.usecase\\.searchopt\\.domain\\..*;" +
  "de\\.awagen\\.kolibri\\.base\\.usecase\\.searchopt\\.parse\\.ParsingConfig;" +
  "de\\.awagen\\.kolibri\\.base\\.actors\\.flows\\.GenericFlows"

// defining fixed env vars for test scope
envVars in Test := Map("PROFILE" -> "test")

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
  //HttpRequest.class included since assembly plugin exited due to conflict on akka-http-core library, but was same version (maybe conflict between test and runtime?)
  case x if x.endsWith("HttpRequest.class") =>
    MergeStrategy.first
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
  ("Akka Snapshot Repository" at "https://repo.akka.io/snapshots/").withAllowInsecureProtocol(false),
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.bintrayIvyRepo("kamon-io", "sbt-plugins"))

val additionalDependencies = Seq(
  //akka-management there to host HTTP endpoints used during bootstrap process
  "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion,
  //helps forming (or joining to) a cluster using akka discovery to discover peer nodes
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion,
  //akka-management-cluster-http is extension to akka-management and allows interaction with cluster through HTTP interface
  //thus it is not necessary but might be conveniant
  // (https://doc.akka.io/docs/akka-management/current/cluster-http-management.html)
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,
  // tag-based aws discovery
  "com.lightbend.akka.discovery" %% "akka-discovery-aws-api" % akkaManagementVersion,
  // k8s discovery
  "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % akkaManagementVersion,
  // to discover other members of the cluster
  "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion, // needed to use cluster singleton (https://doc.akka.io/docs/akka/2.5/cluster-singleton.html?language=scala)
  "com.typesafe.akka" %% "akka-contrib" % akkaContribVersion,
  "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-pki" % akkaVersion,
  "com.typesafe.akka" %% "akka-coordination" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf-v3" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  //scala test framework (scalactic is recommended but not required)(http://www.scalatest.org/install)
  "org.scalactic" %% "scalactic" % scalaTestVersion % Test,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "com.chuusai" %% "shapeless" % shapelessVersion,
  "io.altoo" %% "akka-kryo-serialization" % kryoSerializationVersion,
  "com.amazonaws" % "aws-java-sdk" % awsSdkVersion,
  "com.google.cloud" % "google-cloud-storage" % googleCloudStorageClientVersion,
  // kolibri datatypes
  "de.awagen.kolibri" %% "kolibri-datatypes" % kolibriDatatypesVersion,
  "org.slf4j" % "slf4j-api" % sl4jApiVersion,
  "commons-io" % "commons-io" % apacheCommonsIOVersion,
  "io.kamon" %% "kamon-bundle" % kamonVersion,
  "io.kamon" %% "kamon-prometheus" % kamonVersion,
  "com.softwaremill.macwire" %% "macros" % macwireVersion,
  "com.softwaremill.macwire" %% "util" % macwireVersion,
  "org.scalamock" %% "scalamock" % scalaMockVersion % Test,
  "org.scoverage" %% "scalac-scoverage-runtime" % scalacScoverageRuntimeVersion % Test
)

name := "kolibri-base"
libraryDependencies ++= additionalDependencies
resolvers ++= additionalResolvers
mainClass in assembly := Some("de.awagen.kolibri.base.cluster.ClusterNode")

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
    url("https://github.com/awagen/kolibri-base"),
    "scm:git@github.com:awagen/kolibri-base.git"
  )
)
description := "kolibri-base provides the execution mechanism for the the kolibri project. Kolibri provides a clusterable job execution framework based on Akka."
homepage := Some(url("https://github.com/awagen/kolibri-base"))
// ---- end settings for publishing to mvn central
