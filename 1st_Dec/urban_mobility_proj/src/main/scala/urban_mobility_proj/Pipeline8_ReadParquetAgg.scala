package urban_mobility_proj

import org.apache.spark.sql.SparkSession

object Pipeline8_ReadParquetAgg extends App {

  val spark = SparkSession.builder.master("local[*]").appName("Parquet Aggs").getOrCreate()

  val pq = spark.read.parquet("parquet/final_trips.parquet")

  pq.groupBy("vehicleType").avg("fareAmount").show()

  spark.stop()
}
