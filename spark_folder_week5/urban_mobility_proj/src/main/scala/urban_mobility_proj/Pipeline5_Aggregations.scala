package urban_mobility_proj

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object Pipeline5_Aggregations extends App {

  val spark = SparkSession.builder
    .master("local[*]")
    .appName("Aggregations")
    .getOrCreate()

  import spark.implicits._   // ✅ REQUIRED for $"colName"

  val df = spark.read.parquet("parquet/trips_with_duration.parquet")

  // 1) Average trip distance by vehicleType
  df.groupBy("vehicleType")
    .avg("distanceKm")
    .show()

  // 2) Revenue per day
  df.groupBy(to_date($"startTime").as("tripDay"))
    .sum("fareAmount")
    .show()

  // 3) Top 5 most used routes
  df.groupBy("startLocation", "endLocation")
    .count()
    .orderBy(desc("count"))
    .show(5)

  spark.stop()
}
