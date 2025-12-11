import org.apache.spark.sql.{SaveMode, SparkSession}

object Pipeline2_KeyspacesToParquet {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Pipeline2_KeyspacesToParquet")
      .master("local[*]")
      .config("spark.cassandra.connection.host","cassandra.us-east-1.amazonaws.com")
      .config("spark.cassandra.connection.port","9142")
      .config("spark.cassandra.connection.ssl.enabled","true")
      .getOrCreate()

    val salesDf = spark.read
      .format("org.apache.spark.sql.cassandra")
      .option("keyspace", "retail")
      .option("table", "sales_data")
      .load()

    val selected = salesDf.select("customer_id", "order_id", "amount", "product_name", "quantity")

    selected.write
      .mode(SaveMode.Overwrite)
      .partitionBy("customer_id")
      .parquet("s3a://retail-output/sales/parquet/")

    spark.stop()
  }
}
