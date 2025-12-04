package scala
import org.apache.spark.sql.{SaveMode, SparkSession}

object Pipeline2_KeyspacesToParquet {

  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Pipeline2_KeyspacesToParquet")
      .master("local[*]")

      // ----------------------------
      // Cassandra (Amazon Keyspaces)
      // ----------------------------
      .config("spark.cassandra.connection.host", "cassandra.us-east-1.amazonaws.com")
      .config("spark.cassandra.connection.port", "9142")
      .config("spark.cassandra.connection.ssl.enabled", "true")
      .config("spark.cassandra.auth.username", "rohan_payoda_aws-at-600222957365")
      .config("spark.cassandra.auth.password", "R6KLjkT/fYKVHPFfYIIksjlaP2B6skyZZI/Aeox10IpSH9LJ0kl7/1Tsz3o=")

      // ----------------------------
      // S3 CONFIG (ap-south-2 bucket)
      // ----------------------------
      // IAM credentials
      .config("spark.hadoop.fs.s3a.access.key", "AKIAYXQATP42TY47RN7C")
      .config("spark.hadoop.fs.s3a.secret.key", "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")

      // Correct region + endpoint
      .config("spark.hadoop.fs.s3a.region", "ap-south-2")
      .config("spark.hadoop.fs.s3a.endpoint", "s3.ap-south-2.amazonaws.com")

      // Required S3A configs
      .config("spark.hadoop.fs.s3a.path.style.access", "true")
      .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
      .config("spark.hadoop.fs.s3a.multipart.size", "104857600")   // 100 MB chunk size
      .config("spark.hadoop.fs.s3a.aws.credentials.provider",
        "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider")
      .getOrCreate()

    import spark.implicits._

    // ----------------------------
    // Read from Keyspaces (Cassandra)
    // ----------------------------
    val salesDf = spark.read
      .format("org.apache.spark.sql.cassandra")
      .option("keyspace", "retail")
      .option("table", "sales_data")
      .load()

    println("-----------------salesdf---------------")
    println(salesDf)

    // ----------------------------
    // Select necessary columns
    // ----------------------------
    val selected = salesDf.select(
      "customer_id",
      "order_id",
      "amount",
      "product_name",
      "quantity"
    )

    println("selectedrows",selected)

    // ----------------------------
    // Write Parquet files to S3
    // ----------------------------
    selected.write
      .mode(SaveMode.Overwrite)
      .partitionBy("customer_id")
      .parquet("s3a://rohan-bucket-payoda/sales/parquet/")

    spark.stop()
  }
}
