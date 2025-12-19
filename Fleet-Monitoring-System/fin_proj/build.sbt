// =====================================================
// GLOBAL FIXES
// =====================================================

// Remove logback everywhere (conflicts with Spark)
ThisBuild / libraryDependencies ~= (_.filterNot(_.name.contains("logback")))

// Remove conflicting logback dependencies only
excludeDependencies ++= Seq(
  ExclusionRule("ch.qos.logback", "logback-classic"),
  ExclusionRule("ch.qos.logback", "logback-core")
)

// ADD SLF4J BINDING REQUIRED BY SPARK (IMPORTANT)
libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "1.7.36"

// Force Java 11 for Spark 3.2.1
javaHome := Some(file("/usr/local/opt/openjdk@11"))

// SBT Console settings
ThisBuild / logLevel := Level.Info
run / outputStrategy := Some(StdoutOutput)
run / connectInput := true


// =====================================================
// BUILD SETTINGS
// =====================================================

ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := "2.13.12"
ThisBuild / organization := "com.smartfleet"
ThisBuild / PB.protocVersion := "3.21.12"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked"
  ),
  javacOptions ++= Seq("-source", "11", "-target", "11"),
  resolvers ++= Seq(
    "Confluent Maven Repository" at "https://packages.confluent.io/maven/",
    "Maven Central" at "https://repo1.maven.org/maven2/",
    "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"
  )
)

lazy val versions = new {
  val akka = "2.8.5"
  val akkaHttp = "10.5.3"
  val spark = "3.2.1"
  val kafka = "3.6.0"
  val play = "2.9.0"
  val mysql = "8.0.33"
  val dynamoDb = "2.21.15"
  val protobuf = "3.25.1"
  val aws = "2.21.15"
  val scalaTest = "3.2.17"
}


// =====================================================
// DEPENDENCIES
// =====================================================

lazy val commonDependencies = Seq(
  "com.typesafe" % "config" % "1.4.3",
  "org.scalatest" %% "scalatest" % versions.scalaTest % Test
)

lazy val jsonDependencies = Seq(
  "io.spray" %% "spray-json" % "1.3.6"
)

lazy val akkaDependencies = Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % versions.akka,
  "com.typesafe.akka" %% "akka-stream" % versions.akka,
  "com.typesafe.akka" %% "akka-http" % versions.akkaHttp,
  "com.typesafe.akka" %% "akka-http-spray-json" % versions.akkaHttp
)

// Spark dependencies
lazy val sparkDependencies = Seq(
  "org.apache.spark" %% "spark-core" % versions.spark,
  "org.apache.spark" %% "spark-sql" % versions.spark,
  "org.apache.spark" %% "spark-streaming" % versions.spark,
  "org.apache.spark" %% "spark-sql-kafka-0-10" % versions.spark,
  "org.apache.spark" %% "spark-token-provider-kafka-0-10" % versions.spark
)

lazy val kafkaDependencies = Seq(
  "org.apache.kafka" % "kafka-clients" % versions.kafka,
  "org.apache.kafka" %% "kafka" % versions.kafka
)

lazy val dbDependencies = Seq(
  "mysql" % "mysql-connector-java" % versions.mysql,
  "software.amazon.awssdk" % "dynamodb" % versions.dynamoDb,
  "software.amazon.awssdk" % "s3" % versions.aws
)

lazy val protobufDependencies = Seq(
  "com.google.protobuf" % "protobuf-java" % versions.protobuf
)

lazy val playDependencies = Seq(
  guice,
  "com.typesafe.play" %% "play" % versions.play,
  "com.typesafe.play" %% "play-json" % "2.10.3",
  "com.typesafe.play" %% "play-ws" % versions.play
)


// =====================================================
// PROJECT MODULES
// =====================================================

lazy val root = (project in file("."))
  .aggregate(
    common,
    vehicleSimulator,
    streamingPipeline,
    pipeline2,
    pipeline3,
    pipeline4,
    apiService
  )
  .settings(
    name := "smart-fleet-monitoring"
  )

lazy val common = (project in file("modules/common"))
  .settings(
    commonSettings,
    name := "smart-fleet-common",
    libraryDependencies ++= commonDependencies ++ jsonDependencies ++ dbDependencies ++ protobufDependencies ++ Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.13"
    ),

    // ✅ ScalaPB compiler (must be here, nowhere else)
    PB.targets in Compile := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value
    )
  )

lazy val vehicleSimulator = (project in file("modules/vehicle-simulator"))
  .dependsOn(common)
  .settings(
    commonSettings,
    name := "vehicle-simulator",
    libraryDependencies ++= commonDependencies ++ akkaDependencies ++ kafkaDependencies,
    mainClass := Some("com.smartfleet.simulator.VehicleSimulatorApp")
  )

lazy val streamingPipeline = (project in file("modules/streaming-pipeline"))
  .dependsOn(common)
  .settings(
    commonSettings,
    name := "streaming-pipeline",
    libraryDependencies ++= commonDependencies ++ sparkDependencies ++ kafkaDependencies ++ dbDependencies ++ protobufDependencies,
    mainClass := Some("com.smartfleet.streaming.StreamingPipelineApp"),
    run / fork := true,
    run / javaOptions ++= baseJvmOpts
  )



lazy val pipeline2 = (project in file("modules/pipeline2"))
  .dependsOn(common)
  .settings(
    commonSettings,
    name := "pipeline2",
    libraryDependencies ++= commonDependencies ++ sparkDependencies ++ kafkaDependencies ++ dbDependencies,
    mainClass := Some("com.smartfleet.pipeline2.Pipeline2App"),
    run / fork := true,
    run / javaOptions ++= baseJvmOpts
  )

lazy val pipeline3 = (project in file("modules/pipeline3"))
  .dependsOn(common)
  .settings(
    commonSettings,
    name := "pipeline3",
    libraryDependencies ++= commonDependencies ++ sparkDependencies ++ dbDependencies,
    mainClass := Some("com.smartfleet.pipeline3.Pipeline3App"),
    run / fork := true,
    run / javaOptions ++= baseJvmOpts
  )

lazy val pipeline4 = (project in file("modules/pipeline4"))
  .dependsOn(common)
  .settings(
    commonSettings,
    name := "pipeline4",
    libraryDependencies ++= commonDependencies ++ sparkDependencies ++ dbDependencies ++ jsonDependencies,
    mainClass := Some("com.smartfleet.pipeline4.Pipeline4App"),
    run / fork := true,
    run / javaOptions ++= baseJvmOpts
  )

lazy val apiService = (project in file("modules/api-service"))
  .dependsOn(common)
  .enablePlugins(PlayScala)
  .settings(
    commonSettings,
    name := "api-service",
    libraryDependencies ++= commonDependencies ++ playDependencies ++ dbDependencies,
    Compile / doc / sources := Seq.empty,
    Compile / packageDoc / publishArtifact := false
  )


// =====================================================
// JVM OPTIONS (shared)
// =====================================================

lazy val baseJvmOpts = Seq(
  "--add-opens=java.base/java.lang=ALL-UNNAMED",
  "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
  "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
  "--add-opens=java.base/java.io=ALL-UNNAMED",
  "--add-opens=java.base/java.net=ALL-UNNAMED",
  "--add-opens=java.base/java.nio=ALL-UNNAMED",
  "--add-opens=java.base/java.util=ALL-UNNAMED",
  "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
  "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
)


// =====================================================
// COMMAND ALIASES (FIXED)
// =====================================================

addCommandAlias("runSimulator",  "vehicleSimulator/run")
addCommandAlias("runStreaming",  "streamingPipeline/run")
addCommandAlias("runPipeline2",  "pipeline2/run")
addCommandAlias("runPipeline3",  "pipeline3/run")
addCommandAlias("runPipeline4",  "pipeline4/run")
addCommandAlias("runApi",        "apiService/run")
