package urban_mobility_proj

import org.apache.spark.sql.SparkSession

object Pipeline7_WriteParquet extends App {

  val spark = SparkSession.builder.master("local[*]").appName("Write Parquet").getOrCreate()

  val df = spark.read.parquet("parquet/trips_with_duration.parquet")

  df.write.mode("overwrite").parquet("parquet/final_trips.parquet")

  spark.stop()
}
