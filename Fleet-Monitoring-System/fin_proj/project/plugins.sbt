// SBT plugins for Smart Fleet Monitoring System

// Assembly plugin for creating fat JARs
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.3")

// Play Framework plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.9.0")

// Docker packaging
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")


// Code formatting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

// Dependency updates
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")

// Test coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.9")

// BuildInfo plugin to generate build information
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.13"
