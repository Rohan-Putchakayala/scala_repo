name := "visitor-management-system"
version := "1.0-SNAPSHOT"

scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(messagingService)
  .aggregate(messagingService)

lazy val messagingService = project
  .in(file("messaging-service"))
  .settings(
    scalaVersion := "2.13.12",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.6.20",
      "org.apache.kafka" % "kafka-clients" % "3.3.1",
      "com.typesafe.play" %% "play-json" % "2.9.4",
      "ch.qos.logback" % "logback-classic" % "1.2.11"
    )
  )

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.akka" %% "akka-actor" % "2.6.20",
  "com.typesafe.akka" %% "akka-slf4j" % "2.6.20",
  "org.apache.kafka" % "kafka-clients" % "3.3.1",
  "com.typesafe.play" %% "play-slick" % "5.0.2",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.2",
  "mysql" % "mysql-connector-java" % "8.0.33",
  "com.typesafe.play" %% "play-mailer" % "8.0.1",
  "com.typesafe.play" %% "play-mailer-guice" % "8.0.1"
)