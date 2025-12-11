ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.18"

lazy val root = (project in file("."))
  .settings(
    name := "DEAssignment2",

    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-core" % "3.4.1",
      "org.apache.spark" %% "spark-sql" % "3.4.1"
    ),

    fork := true,

    run / javaOptions ++= Seq(
      "--add-opens=java.base/javax.security.auth=ALL-UNNAMED",
      "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
      "--enable-native-access=ALL-UNNAMED"
    )
  )
