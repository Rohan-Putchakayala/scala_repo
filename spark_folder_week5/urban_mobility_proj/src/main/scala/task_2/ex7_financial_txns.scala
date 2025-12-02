package task_2

import org.apache.spark.sql.{SaveMode, SparkSession}
import scala.util.Random
import java.time._

object ex7_financial_txns {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("ex7_financial_txns")
      .config("spark.ui.port", "4040")
      .master("local[*]")
      .getOrCreate()
    import spark.implicits._

    val numTxns = 5000000
    val accounts = (1 to 100000).toArray
    val txnTypes = Array("DEPOSIT", "WITHDRAWAL", "TRANSFER")

    val txnsRDD = spark.sparkContext.parallelize(1 to numTxns, 50).map { _ =>
      val accountId = accounts(Random.nextInt(accounts.length))
      val txnType = txnTypes(Random.nextInt(txnTypes.length))
      val amount = (Random.nextDouble() * 10000).formatted("%.2f").toDouble
      val ts = LocalDateTime.now().minusMinutes(Random.nextInt(1000000))
      (accountId, txnType, amount, ts.toString)
    }

    val txnsDF = txnsRDD.toDF("accountId", "txnType", "amount", "timestamp")

    // Aggregations
    val txnSummary = txnsDF.groupBy("accountId")
      .agg(org.apache.spark.sql.functions.sum("amount").alias("totalAmount"))

    // Write outputs
    txnSummary.write.mode(SaveMode.Overwrite).parquet("output/ex7/parquet")
    txnSummary.write.mode(SaveMode.Overwrite).json("output/ex7/json")
    Thread.sleep(5000000)

    spark.stop()
  }
}
