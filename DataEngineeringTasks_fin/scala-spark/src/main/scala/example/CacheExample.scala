package example

import org.apache.spark.sql.{SparkSession, functions => F}

object CacheExample {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("CacheExample")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val sales = Seq(
      (101, "P1", 2, 300.0),
      (101, "P2", 1, 100.0),
      (102, "P1", 1, 150.0),
      (103, "P3", 5, 500.0)
    ).toDF("customerId", "productId", "quantity", "amount")

    // Cache dataset
    sales.cache()

    // Total amount per customer
    val spent = sales.groupBy("customerId")
      .agg(F.sum("amount").as("totalSpent"))

    spent.show(false)

    // Total quantity per product
    val qty = sales.groupBy("productId")
      .agg(F.sum("quantity").as("totalQuantity"))

    qty.show(false)

    spark.stop()
  }
}
