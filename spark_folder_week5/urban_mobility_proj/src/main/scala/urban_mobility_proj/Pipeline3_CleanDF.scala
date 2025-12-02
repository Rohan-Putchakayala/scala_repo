////package com.UrbanMoveSpark
////
////import org.apache.spark.sql.SparkSession
////
////object Pipeline3_CleanDF extends App {
////
////  val spark = SparkSession.builder.master("local[*]").appName("Clean DF").getOrCreate()
////  import spark.implicits._
////  import org.apache.spark.sql.functions._
////
////  val df = spark.read.parquet("parquet/trips_raw_df.parquet")
////
////  val cleaned = df
////    .filter($"distanceKm" > 0)
////    .filter($"fareAmount" >= 0)
////    .filter(unix_timestamp($"startTime") < unix_timestamp($"endTime"))
////
////  cleaned.write.mode("overwrite").parquet("parquet/trips_clean.parquet")
////
////  spark.stop()
////}
//
//
//package com.UrbanMoveSpark
//
//import org.apache.spark.sql.SparkSession
//
//object Pipeline3_CleanDF extends App {
//
//  val spark = SparkSession.builder.master("local[*]").appName("Clean DF").getOrCreate()
//  import spark.implicits._
//  import org.apache.spark.sql.functions._
//
//  val df = spark.read.parquet("parquet/trips_raw_df.parquet")
//
//  println("=== RAW ROW COUNT ===")
//  println(df.count())
//
//  df.printSchema()
//  df.show(20, false)
//
//  // 1. Check distance filter
//  val c1 = df.filter($"distanceKm" > 0)
//  println("After distance > 0: " + c1.count())
//
//  // 2. Check fare filter
//  val c2 = c1.filter($"fareAmount" >= 0)
//  println("After fareAmount >= 0: " + c2.count())
//
//  // 3. Check timestamp conversion
//  val c3 = c2.withColumn("start_ts", unix_timestamp($"startTime"))
//    .withColumn("end_ts", unix_timestamp($"endTime"))
//
//  c3.select("startTime", "endTime", "start_ts", "end_ts").show(20, false)
//
//  val c4 = c3.filter($"start_ts" < $"end_ts")
//  println("After timestamp validation: " + c4.count())
//
//  // This is your cleaned DF (if any remain)
//  val cleaned = c4.drop("start_ts", "end_ts")
//
//  cleaned.write.mode("overwrite").parquet("parquet/trips_clean.parquet")
//
//  spark.stop()
//}


package urban_mobility_proj

import org.apache.spark.sql.SparkSession

object Pipeline3_CleanDF extends App {

  val spark = SparkSession.builder
    .master("local[*]")
    .appName("Clean DF")
    .getOrCreate()

  import spark.implicits._
  import org.apache.spark.sql.functions._

  // Read raw parquet
  val df = spark.read.parquet("parquet/trips_raw_df.parquet")

  println("=== RAW ROW COUNT ===")
  println(df.count())

  df.printSchema()
  df.show(20, false)

  // Correct timestamp parsing format
  val parsed = df
    .withColumn("start_ts",
      to_timestamp($"startTime", "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    )
    .withColumn("end_ts",
      to_timestamp($"endTime", "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    )

  println("=== After timestamp parsing ===")
  parsed.select("startTime", "start_ts", "endTime", "end_ts").show(20, false)

  // Filtering logic
  val cleaned = parsed
    .filter($"distanceKm" > 0)
    .filter($"fareAmount" >= 0)
    .filter($"start_ts".isNotNull && $"end_ts".isNotNull)
    .filter($"start_ts" < $"end_ts")
    .drop("start_ts", "end_ts")

  println("=== CLEANED ROW COUNT ===")
  println(cleaned.count())

  cleaned.show(20, false)

  cleaned.write.mode("overwrite").parquet("parquet/trips_clean.parquet")

  println("=== Saved cleaned DF ===")

  spark.stop()
}
