package urban_mobility_proj

import org.apache.spark.sql.{SparkSession}

object Pipeline2_RDDtoDF extends App {

  val spark = SparkSession.builder.master("local[*]").appName("RDD to DF").getOrCreate()
  import spark.implicits._

  val rdd = spark.sparkContext.textFile("urbanmove_trips.csv")
  val header = rdd.first()

  val df = rdd.filter(_ != header).map(_.split(",")).map { cols =>
    Trip(
      cols(0).toLong,
      cols(1).toInt,
      cols(2),
      cols(3),
      cols(4),
      cols(5),
      cols(6),
      cols(7).toDouble,
      cols(8).toDouble,
      cols(9),
      cols(10).toDouble
    )
  }.toDF()

  df.show(5)

  df.write.mode("overwrite").parquet("parquet/trips_raw_df.parquet")

  spark.stop()
}
