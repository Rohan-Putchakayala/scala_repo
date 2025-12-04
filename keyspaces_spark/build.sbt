import scala.collection.immutable.Seq

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.10"

val sparkVersion = "3.2.1"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.scalatest" %% "scalatest" % "3.2.2" % "test",
  "com.datastax.spark" %% "spark-cassandra-connector" % "3.0.1",
  "com.github.jnr" % "jnr-posix" % "3.1.7",
  "joda-time" % "joda-time" % "2.10.10",
  "org.apache.spark" %% "spark-streaming" % sparkVersion,
  "org.apache.spark" %% "spark-streaming-kafka-0-10" % sparkVersion,
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,
  "com.typesafe" % "config" % "1.4.2",
  "mysql" % "mysql-connector-java" % "8.0.33",

  // Avro support
  "org.apache.spark" %% "spark-avro" % sparkVersion,


  "org.apache.hadoop" % "hadoop-common" % "3.3.1",
  "org.apache.hadoop" % "hadoop-aws" % "3.3.1",
  "com.amazonaws" % "aws-java-sdk-bundle" % "1.11.375"
)

lazy val root = (project in file("."))
  .settings(
    name := "aws-keyspaces-spark-project"
  )