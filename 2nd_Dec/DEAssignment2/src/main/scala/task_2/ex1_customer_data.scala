package task_2

import org.apache.spark.sql.{SaveMode, SparkSession}
import scala.util.Random

object ex1_customer_data {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("ex1_customer_data")
      .master("local[*]")
      .getOrCreate()
    import spark.implicits._

    val numRecords = 5000000
    val cities = (1 to 50).map(i => "City_" + i).toArray

    val customersRDD = spark.sparkContext
      .parallelize(1 to numRecords, 50)
      .map { id =>
        val name = Random.alphanumeric.take(10).mkString
        val age = 18 + Random.nextInt(53)
        val city = cities(Random.nextInt(cities.length))
        (id.toLong, name, age, city)
      }

    val customersDF = customersRDD.toDF("customerId", "name", "age", "city")

    // RDD city counts
    val rddCounts = customersRDD
      .map(t => (t._4, 1))
      .reduceByKey(_ + _)

    // DF city counts
    val dfCounts = customersDF.groupBy("city").count()

    // Write outputs
    dfCounts.write.mode(SaveMode.Overwrite).csv("output/ex1/csv")
    dfCounts.write.mode(SaveMode.Overwrite).json("output/ex1/json")
    dfCounts.write.mode(SaveMode.Overwrite).parquet("output/ex1/parquet")

    Thread.sleep(5000000)

    spark.stop()
  }
}
