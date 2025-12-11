package urban_mobility_proj

import org.apache.spark.sql.SparkSession

object Pipeline6_SQLQueries extends App {

  val spark = SparkSession.builder.master("local[*]").appName("SQL Queries").getOrCreate()

  val df = spark.read.parquet("parquet/trips_with_duration.parquet")

  df.createOrReplaceTempView("trips")

  spark.sql("SELECT vehicleType, COUNT(*) FROM trips GROUP BY vehicleType").show()

  spark.sql("SELECT startLocation, endLocation, AVG(fareAmount) AS avgFare FROM trips GROUP BY startLocation, endLocation").show()

  spark.sql("SELECT paymentMethod, COUNT(*) FROM trips GROUP BY paymentMethod").show()

  spark.stop()
}
