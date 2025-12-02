ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := "2.13.13"

lazy val root = (project in file("."))
  .settings(
    name := "notification-service",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
      "com.typesafe.akka" %% "akka-stream" % "2.8.5",
      "com.typesafe.akka" %% "akka-slf4j" % "2.8.5",
      "com.typesafe.akka" %% "akka-serialization-jackson" % "2.8.5",
      "com.typesafe.akka" %% "akka-stream-kafka" % "4.0.2",
      "org.apache.kafka" % "kafka-clients" % "3.6.1",
      "com.typesafe.play" %% "play-json" % "2.10.4",
      "com.sun.mail" % "javax.mail" % "1.6.2",
      "ch.qos.logback" % "logback-classic" % "1.4.14",
      "com.typesafe" % "config" % "1.4.3"
    ),
    assembly / mainClass := Some("NotificationServiceApp"),
    assembly / assemblyJarName := "notification-service-assembly.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case PathList("reference.conf") => MergeStrategy.concat
      case PathList("application.conf") => MergeStrategy.concat
      case x => MergeStrategy.first
    },
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:implicitConversions",
      "-language:postfixOps"
    )
  )
