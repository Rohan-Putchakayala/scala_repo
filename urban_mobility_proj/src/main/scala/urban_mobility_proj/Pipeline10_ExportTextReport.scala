package urban_mobility_proj

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object Pipeline10_ExportTextReport extends App {

  val spark = SparkSession.builder.master("local[*]").appName("Export Report").getOrCreate()

  val df = spark.read.parquet("parquet/final_trips.parquet")

  val report = df.groupBy("vehicleType").sum("fareAmount")

  report.rdd.saveAsTextFile("reports/vehicle_revenue_report.txt")

  spark.stop()
}
