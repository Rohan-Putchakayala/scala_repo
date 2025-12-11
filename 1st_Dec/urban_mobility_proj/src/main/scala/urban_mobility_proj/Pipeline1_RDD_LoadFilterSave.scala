package urban_mobility_proj

import org.apache.spark.sql.SparkSession

object Pipeline1_RDD_LoadFilterSave extends App {

  val spark = SparkSession.builder.appName("RDD Load Filter Save").master("local[*]").getOrCreate()
  val sc = spark.sparkContext

  val rdd = sc.textFile("urbanmove_trips.csv")

  val header = rdd.first()
  val data = rdd.filter(_ != header)

  val filtered = data
    .map(_.split(","))
    .filter(cols => cols(7).toDouble > 10)
    .map(cols => (cols(2), cols(7).toDouble))

  filtered.saveAsTextFile("output/rdd_distance_over_10")

  spark.stop()
}
