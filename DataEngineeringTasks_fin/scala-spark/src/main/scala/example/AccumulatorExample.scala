package example

import org.apache.spark.sql.SparkSession

object AccumulatorExample {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("AccumulatorExample")
      .master("local[*]")
      .getOrCreate()
    val sc = spark.sparkContext
    val data = sc.parallelize(List(100, 550, 800, 200, 999, 50))
    val threshold = 500
    val accumulator = sc.longAccumulator("HighValue")

    data.foreach { x =>
      if (x > threshold) accumulator.add(1)
    }

    println(s"Transactions > $threshold: ${accumulator.value}")

    spark.stop()
  }
}
