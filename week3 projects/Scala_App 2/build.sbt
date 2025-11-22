ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.13"

val AkkaVersion = "2.8.5"
val PlayVersion = "2.9.1"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := "room-management-system",
    libraryDependencies ++= Seq(
      guice,
      "com.typesafe.play" %% "play" % PlayVersion,
      "com.typesafe.play" %% "play-slick" % "5.3.0",
      "com.typesafe.play" %% "play-slick-evolutions" % "5.3.0",
      "com.h2database" % "h2" % "2.2.224",
      "org.postgresql" % "postgresql" % "42.7.1",
      "mysql" % "mysql-connector-java" % "8.0.33",
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "org.apache.kafka" % "kafka-clients" % "3.6.1",
      "com.typesafe.akka" %% "akka-stream-kafka" % "4.0.2",
      "com.typesafe.play" %% "play-json" % "2.10.4",
      "commons-io" % "commons-io" % "2.15.1",
      "com.sun.mail" % "javax.mail" % "1.6.2"
    )
  )

lazy val messagingService = (project in file("messaging-service"))
  .settings(
    name := "messaging-service",
    scalaVersion := "2.13.13",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "org.apache.kafka" % "kafka-clients" % "3.6.1",
      "com.typesafe.akka" %% "akka-stream-kafka" % "4.0.2",
      "ch.qos.logback" % "logback-classic" % "1.4.14",
      "com.typesafe" % "config" % "1.4.3",
      "com.typesafe.play" %% "play-json" % "2.10.4"
    )
  )
