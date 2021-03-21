logLevel := Level.Warn

resolvers += ("Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/").withAllowInsecureProtocol(false)
resolvers += ("Artima Maven Repository" at "https://repo.artima.com/releases").withAllowInsecureProtocol(false)

//provide dependency graph  (e.g "sbt dependencyBrowseGraph", "sbt dependencyTree")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

