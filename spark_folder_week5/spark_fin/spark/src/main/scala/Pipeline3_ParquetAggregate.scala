import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.sql.functions._

object Pipeline3_ParquetAggregate {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Pipeline3_ParquetAggregate")
      .master("local[*]")
      .getOrCreate()

    val df = spark.read.parquet("s3a://retail-output/sales/parquet/")

    // Assume amount is per unit price; adjust if it's per-order
    val agg = df.groupBy("product_name")
      .agg(
        sum("quantity").alias("total_quantity"),
        round(sum(expr("amount * quantity")),2).alias("total_revenue")
      )
      .orderBy(desc("total_revenue"))

    agg.coalesce(1)
      .write.mode(SaveMode.Overwrite)
      .json("s3a://retail-output/aggregates/products.json")

    spark.stop()
  }
}
