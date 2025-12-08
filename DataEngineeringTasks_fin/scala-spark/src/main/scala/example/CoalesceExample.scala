package example

import org.apache.spark.sql.SparkSession

object CoalesceExample {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("CoalesceExample")
      .master("local[*]")
      .getOrCreate()

    // Large dataset
    val logs = spark.range(1, 1000000).toDF("id")

    val filtered = logs.filter("id < 1000")

    println(s"Original partitions: ${filtered.rdd.getNumPartitions}")

    // Reduce partitions without shuffle
    val reduced = filtered.coalesce(2)

    println(s"After coalesce: ${reduced.rdd.getNumPartitions}")

    reduced.write
      .mode("overwrite")
      .csv("output/logs_small")

    spark.stop()
  }
}
