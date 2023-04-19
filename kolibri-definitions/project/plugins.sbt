logLevel := Level.Warn

resolvers += ("Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/").withAllowInsecureProtocol(false)
resolvers += ("Artima Maven Repository" at "https://repo.artima.com/releases").withAllowInsecureProtocol(false)

//provide dependency graph  (e.g "sbt dependencyBrowseGraph", "sbt dependencyTree")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

// signing artefacts
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")

// aspectj instrumentation (https://kamon.io/docs/latest/guides/installation/plain-application/#the-sbt-plugin)
addSbtPlugin("io.kamon" % "sbt-kanela-runner" % "2.0.9")

// code coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.8.2")
