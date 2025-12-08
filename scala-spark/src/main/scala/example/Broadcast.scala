package example

import org.apache.spark.sql.{SparkSession, functions => F}

object Broadcast {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("BroadcastExample")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    // Large dataset
    val transactions = Seq(
      ("t1", 100.0, "EUR"),
      ("t2", 200.0, "INR"),
      ("t3", 300.0, "USD"),
      ("t4", 250.0, "EUR")
    ).toDF("txnId", "amount", "currency")

    // Small dataset → broadcast
    val exchangeRates = Map(
      "USD" -> 1.0,
      "EUR" -> 1.07,
      "INR" -> 0.012
    )

    val broadcastRates = spark.sparkContext.broadcast(exchangeRates)

    // Convert using broadcast
    val converted = transactions.map { row =>
      val amt = row.getAs[Double]("amount")
      val curr = row.getAs[String]("currency")
      val rate = broadcastRates.value(curr)
      val usdAmt = amt * rate
      (row.getAs[String]("txnId"), usdAmt, curr)
    }.toDF("txnId", "amountUSD", "currency")

    // Count per currency
    val result = converted.groupBy("currency").count()
    result.show(false)

    spark.stop()
  }
}
