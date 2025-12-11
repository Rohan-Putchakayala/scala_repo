package task_2

import org.apache.spark.sql.{SaveMode, SparkSession}
import scala.util.Random
import org.apache.spark.sql.functions._

object ex2_sales_data {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("ex2_sales_data")
      .master("local[*]")
      .getOrCreate()
    import spark.implicits._

    val numSales = 10000000
    val stores = (1 to 100).map(i => "Store_" + i).toArray

    // Create RDD of sales data
    val salesRDD = spark.sparkContext
      .parallelize(1 to numSales, 50)
      .map { _ =>
        val store = stores(Random.nextInt(stores.length))
        val amount = Random.nextDouble() * 500
        (store, amount)
      }

    val salesDF = salesRDD.toDF("storeId", "amount")

    // RDD reduceByKey (faster)
    val rddReduceByKey = salesRDD.reduceByKey(_ + _)

    // DataFrame aggregation with proper alias to avoid invalid column names
    val dfTotal = salesDF.groupBy("storeId")
      .agg(sum("amount").alias("totalAmount"))

    // Write to Parquet/CSV/JSON safely
    dfTotal.write.mode(SaveMode.Overwrite).parquet("output/ex2/parquet")
    dfTotal.write.mode(SaveMode.Overwrite).csv("output/ex2/csv")
    dfTotal.write.mode(SaveMode.Overwrite).json("output/ex2/json")
    Thread.sleep(5000000)

    spark.stop()
  }
}
