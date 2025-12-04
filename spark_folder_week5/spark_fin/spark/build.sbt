ThisBuild / scalaVersion := "2.12.15"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.payoda"

val sparkVersion = "3.2.1"

lazy val root = (project in file("."))
  .settings(
    name := "urbanmove-spark"
  )
libraryDependencies ++= Seq(
  // Spark dependencies
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-streaming" % sparkVersion,
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,
  "org.apache.spark" %% "spark-streaming-kafka-0-10" % sparkVersion,

  // Cassandra connector
  "com.datastax.spark" %% "spark-cassandra-connector" % "3.2.0",

  // MySQL JDBC driver
  "mysql" % "mysql-connector-java" % "8.0.33",

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.2" % Test
)