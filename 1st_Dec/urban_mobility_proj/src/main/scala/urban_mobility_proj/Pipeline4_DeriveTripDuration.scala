//package com.UrbanMoveSpark
//
//import org.apache.spark.sql.SparkSession
//import org.apache.spark.sql.functions._
//
//object Pipeline4_DeriveTripDuration extends App {
//
//  val spark = SparkSession.builder
//    .master("local[*]")
//    .appName("Trip Duration")
//    .getOrCreate()
//
//  import spark.implicits._   // ✅ REQUIRED for $"columnName"
//
//  val df = spark.read.parquet("parquet/trips_clean.parquet")
//
//  val df2 = df.withColumn(
//    "tripDurationMinutes",
//    (unix_timestamp($"endTime") - unix_timestamp($"startTime")) / 60
//  )
//
//  df2.show(10)
//
//  df2.write.mode("overwrite").parquet("parquet/trips_with_duration.parquet")
//
//  spark.stop()
//}

package urban_mobility_proj

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object Pipeline4_DeriveTripDuration extends App {

  val spark = SparkSession.builder
    .master("local[*]")
    .appName("Trip Duration")
    .getOrCreate()

  import spark.implicits._

  val df = spark.read.parquet("parquet/trips_clean.parquet")

  println("=== BEFORE PARSING TIMESTAMPS ===")
  df.select("startTime", "endTime").show(10, false)

  // Correct timestamp parsing
  val df2 = df
    .withColumn("start_ts",
      to_timestamp($"startTime", "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    )
    .withColumn("end_ts",
      to_timestamp($"endTime", "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    )
    .withColumn(
      "tripDurationMinutes",
      (col("end_ts").cast("long") - col("start_ts").cast("long")) / 60
    )

  println("=== AFTER PARSING TIMESTAMPS ===")
  df2.select("startTime", "start_ts", "endTime", "end_ts", "tripDurationMinutes").show(10, false)

  df2.write.mode("overwrite").parquet("parquet/trips_with_duration.parquet")

  println("=== Saved trips_with_duration.parquet ===")

  spark.stop()
}

