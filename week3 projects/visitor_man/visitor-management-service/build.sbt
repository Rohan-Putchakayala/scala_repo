name := "visitor-management-service"
organization := "com.vms.management"
version := "1.0-SNAPSHOT"
lazy val root = (project in file(".")).enablePlugins(PlayScala)
scalaVersion := "2.13.16"
val jacksonVersion = "2.14.3"

libraryDependencies ++= Seq(
  guice,
  ws,
  filters,
  // JDBC & Database
  "org.playframework" %% "play-slick"            % "6.1.0",
  "org.playframework" %% "play-slick-evolutions" % "6.1.0",
  "mysql" % "mysql-connector-java" % "8.0.26",
  // Flyway (optional)
  "org.flywaydb" % "flyway-core" % "9.22.0",
  // AWS S3 SDK v2
  "software.amazon.awssdk" % "s3" % "2.20.60",
  // Kafka client
  "org.apache.kafka" % "kafka-clients" % "3.7.0",
  // Akka/Pekko (scheduling and streaming)
  "com.typesafe.akka" %% "akka-stream" % "2.6.20",
  "com.typesafe.akka" %% "akka-actor" % "2.6.20",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.20",
  "com.typesafe.akka" %% "akka-slf4j" % "2.6.21",
  "com.typesafe.akka" %% "akka-stream-kafka" % "4.0.2",
  "com.typesafe.akka" %% "akka-http" % "10.5.1",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.3",
  // JSON
  "com.typesafe.play" %% "play-json" % "2.9.4",
  // Authentication & Security
  "com.github.jwt-scala" %% "jwt-play-json" % "10.0.1",
  "com.github.t3hnar" %% "scala-bcrypt" % "4.3.0",
  // Logging
  "ch.qos.logback" % "logback-classic" % "1.2.13",
  // Test
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.0" % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.20" % Test,
  "org.scalatest" %% "scalatest" % "3.2.15" % Test
)

TwirlKeys.templateImports += "controllers.routes"

dependencyOverrides ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion
)
