package task_2

import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.sql.functions._
import scala.util.Random

object ex10_case_study {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("ex10_case_study")
      .config("spark.ui.port", "4040")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    // ======================================
    // 1. Generate Customers (2 Million)
    // ======================================
    val custCount = 2000000

    val custRDD = spark.sparkContext
      .parallelize(1 to custCount, 50)
      .map { id =>
        val name = Random.alphanumeric.take(8).mkString
        (id, name)
      }

    val custDF = custRDD.toDF("customerId", "name")

    // Optional: Write customers as CSV
    custDF.write.mode(SaveMode.Overwrite)
      .csv("output/ex10/customers")


    // ======================================
    // 2. Generate Transactions (5 Million)
    // ======================================
    val txnCount = 5000000

    val txnRDD = spark.sparkContext
      .parallelize(1 to txnCount, 80)
      .map { tid =>
        val cust = Random.nextInt(custCount) + 1
        val amount = Random.nextDouble() * 1000
        (tid, cust, amount)
      }

    val txnDF = txnRDD.toDF("txnId", "customerId", "amount")

    // Optional: Write raw transactions
    txnDF.write.mode(SaveMode.Overwrite)
      .csv("output/ex10/transactions")


    // ======================================
    // 3. JOIN Customers + Transactions
    // ======================================
    val joined = txnDF.join(custDF, "customerId")

    // ======================================
    // 4. Calculate Total Spend Per Customer
    // ======================================
    val spendPerCustomer = joined
      .groupBy("customerId", "name")
      .agg(sum("amount").alias("totalSpend"))

    // ======================================
    // 5. Save Analytical Output as Parquet
    // ======================================
    spendPerCustomer.write
      .mode(SaveMode.Overwrite)
      .parquet("output/ex10/final_parquet")

    // ======================================
    // 6. Print small sample
    // ======================================
    println("Sample Results:")
    spendPerCustomer.show(5, truncate = false)
    Thread.sleep(5000000)

    spark.stop()
  }
}
