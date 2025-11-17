//ThisBuild / version := "0.1.0-SNAPSHOT"
//
//ThisBuild / scalaVersion := "2.13.13"
//
//lazy val root = (project in file("."))
//  .settings(
//    name := "kafka-consumer-person"
//  )
//
////resolvers += "Akka library repository".at("https://repo.akka.io/maven")
////
////lazy val akkaVersion = sys.props.getOrElse("akka.version", "2.9.3")
////
////// Run in a separate JVM, to make sure sbt waits until all threads have
////// finished before returning.
////// If you want to keep the application running while executing other
////// sbt tasks, consider https://github.com/spray/sbt-revolver/
////fork := true
////
////libraryDependencies ++= Seq(
////  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
////  "ch.qos.logback" % "logback-classic" % "1.2.13",
////  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
////  "org.scalatest" %% "scalatest" % "3.2.15" % Test
////)
//
////libraryDependencies ++= Seq(
////  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
////  "com.typesafe.akka" %% "akka-stream-kafka" % "6.0.0",
////  "com.typesafe.akka" %% "akka-http" % "10.6.3",
////  "com.typesafe.akka" %% "akka-http-spray-json" % "10.6.3",
////  "org.apache.kafka" %% "kafka" % "3.7.0" // Kafka client
////)
//
//resolvers ++= Seq(
//  "Akka Repository" at "https://repo.akka.io/releases/",
//  Resolver.mavenCentral
//)
//
//// Dependencies
//libraryDependencies ++= Seq(
//  // Akka
//  "com.typesafe.akka" %% "akka-actor-typed" % "2.9.3",
//  "com.typesafe.akka" %% "akka-stream" % "2.9.3",
//  "com.typesafe.akka" %% "akka-http" % "10.2.10",             // corrected stable version
//  "com.typesafe.akka" %% "akka-http-spray-json" % "10.2.10", // corrected stable version
//  "com.typesafe.akka" %% "akka-stream-kafka" % "2.1.1",      // corrected version compatible with Akka 2.9.x
//
//  // Logging (optional but recommended for Akka)
//  "ch.qos.logback" % "logback-classic" % "1.4.11"
//)
//
//// Optional: enable pretty printing for SBT
//ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-encoding", "UTF-8")

name := "kafka-consumer-person"

version := "0.1.0"

scalaVersion := "2.13.12"

resolvers ++= Seq(
  "Akka Repository" at "https://repo.akka.io/releases/",
  Resolver.mavenCentral
)

libraryDependencies ++= Seq(
  // Akka
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
  "com.typesafe.akka" %% "akka-stream" % "2.8.5",
  "com.typesafe.akka" %% "akka-http" % "10.2.10",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.2.10",
  "com.typesafe.akka" %% "akka-stream-kafka" % "2.1.1",

  // Logging
  "ch.qos.logback" % "logback-classic" % "1.4.11"
)

ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-encoding", "UTF-8")