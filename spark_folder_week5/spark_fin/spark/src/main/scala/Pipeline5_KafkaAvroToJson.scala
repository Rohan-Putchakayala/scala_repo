import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericDatumReader, GenericRecord}
import org.apache.avro.io.DecoderFactory
import org.apache.commons.codec.binary.Base64

object Pipeline5_KafkaAvroToJson {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Pipeline5_KafkaAvroToJson")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val kafkaBootstrap = "<KAFKA_BOOTSTRAP:9092>"

    val avroSchemaStr =
      """
      {
        "type": "record",
        "name": "OrderRecord",
        "namespace": "com.retail",
        "fields": [
          { "name": "order_id", "type": "int" },
          { "name": "customer_id", "type": "int" },
          { "name": "amount", "type": "double" },
          { "name": "created_at", "type": "string" }
        ]
      }
      """

    val schema = new org.apache.avro.Schema.Parser().parse(avroSchemaStr)

    val df = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaBootstrap)
      .option("subscribe", "orders_avro_topic")
      .option("startingOffsets", "latest")
      .load()

    // Value is base64-encoded Avro bytes (string)
    val decodeUdf = udf((b64: String) => {
      if (b64 == null) null
      else {
        try {
          val bytes = Base64.decodeBase64(b64)
          val reader = new GenericDatumReader[GenericRecord](schema)
          val decoder = DecoderFactory.get().binaryDecoder(bytes, null)
          val record = reader.read(null, decoder)
          val json = s"""{"order_id":${record.get("order_id")},"customer_id":${record.get("customer_id")},"amount":${record.get("amount")},"created_at":"${record.get("created_at")}"}"""
          json
        } catch {
          case _: Throwable => null
        }
      }
    })

    val parsed = df.selectExpr("CAST(value AS STRING) as value_base64")
      .withColumn("json_str", decodeUdf(col("value_base64")))
      .filter(col("json_str").isNotNull)

    val query = parsed.select("json_str").writeStream
      .format("text")
      .option("path", "s3a://retail-output/stream/json/")
      .option("checkpointLocation", "s3a://retail-output/stream/checkpoints/pipeline5/")
      .start()

    query.awaitTermination()
  }
}
