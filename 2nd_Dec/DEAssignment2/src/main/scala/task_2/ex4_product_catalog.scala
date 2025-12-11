package task_2

import org.apache.spark.sql.{SaveMode, SparkSession}
import scala.util.Random

object ex4_product_catalog {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("ex4_product_catalog")
      .master("local[*]")
      .getOrCreate()
    import spark.implicits._

    val numProducts = 2000000
    val categories = Array("Electronics", "Clothing", "Books", "Home", "Toys", "Grocery")

    val productsRDD = spark.sparkContext
      .parallelize(1 to numProducts, 50)
      .map { id =>
        val name = "Product_" + Random.alphanumeric.take(8).mkString
        val category = categories(Random.nextInt(categories.length))
        val price = (Random.nextDouble() * 1000).formatted("%.2f").toDouble
        val stock = Random.nextInt(500)
        (id.toLong, name, category, price, stock)
      }

    val productsDF = productsRDD.toDF("productId", "name", "category", "price", "stock")

    // RDD category counts
    val rddCounts = productsRDD.map(t => (t._3, 1)).reduceByKey(_ + _)

    // DF aggregations
    val dfAgg = productsDF.groupBy("category")
      .agg(
        org.apache.spark.sql.functions.count("*").alias("count"),
        org.apache.spark.sql.functions.avg("price").alias("avgPrice")
      )

    // Write outputs
    dfAgg.write.mode(SaveMode.Overwrite).csv("output/ex4/csv")
    dfAgg.write.mode(SaveMode.Overwrite).json("output/ex4/json")
    dfAgg.write.mode(SaveMode.Overwrite).parquet("output/ex4/parquet")
    Thread.sleep(5000000)

    spark.stop()
  }
}
