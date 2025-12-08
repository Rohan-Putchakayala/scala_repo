package example

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.protobuf.functions.from_protobuf

object KafkaProtobuf {
  def main(args: Array[String]): Unit = {

    // Initialize Spark session
    val spark = SparkSession.builder()
      .appName("KafkaProtobuf")
      .master("local[*]")  // run locally with all cores
      .getOrCreate()

    import spark.implicits._

    // Read data from Kafka topic
    val kafkaDf = spark.read
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("subscribe", "orders")
      .load()

    // Select the raw bytes from Kafka
    val df = kafkaDf.select($"value".as("binary"))

    // Path to the .desc file generated from Order.proto
    val descFilePath = "./src/main/protobuf/Order.desc"

    // Parse the Protobuf binary using Spark Protobuf
    val parsed =
      df.withColumn(
          "order",
          from_protobuf(
            col("binary"),
            "Order",    // fully-qualified message name (no package in your proto)
            descFilePath
          )
        )
        .select("order.*")  // select all fields from the Order message

    // Show the parsed data
    parsed.show(false)

    // Stop Spark session
    spark.stop()
  }
}
