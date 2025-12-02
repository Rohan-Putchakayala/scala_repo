ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := "2.13.13"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := "room-management-service",
    libraryDependencies ++= Seq(
      guice,
      "com.typesafe.play" %% "play" % "2.9.1",
      "com.typesafe.play" %% "play-slick" % "5.3.0",
      "com.typesafe.play" %% "play-slick-evolutions" % "5.3.0",
      "com.typesafe.play" %% "play-json" % "2.10.4",
      "com.h2database" % "h2" % "2.2.224",
      "org.postgresql" % "postgresql" % "42.7.1",
      "mysql" % "mysql-connector-java" % "8.0.33",
      "org.apache.kafka" % "kafka-clients" % "3.6.1",
      "commons-io" % "commons-io" % "2.15.1",
      "ch.qos.logback" % "logback-classic" % "1.4.14",
      "com.typesafe" % "config" % "1.4.3"
    ),
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:implicitConversions",
      "-language:postfixOps"
    )
  )
