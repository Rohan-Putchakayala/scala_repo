import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericDatumWriter}
import org.apache.avro.io.EncoderFactory
import java.io.ByteArrayOutputStream

object Pipeline4_PollMySQL_WriteKafkaAvro {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Pipeline4_PollMySQL_WriteKafkaAvro")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val jdbcUrl = "jdbc:mysql://<RDS_HOST>:3306/retail_db"
    val jdbcProps = new java.util.Properties()
    jdbcProps.setProperty("user", "<MYSQL_USER>")
    jdbcProps.setProperty("password", "<MYSQL_PASS>")
    jdbcProps.setProperty("driver", "com.mysql.cj.jdbc.Driver")

    val kafkaBootstrap = "<KAFKA_BOOTSTRAP:9092>"
    val offsetPath = "s3a://retail-output/stream/offsets/last_order_id.txt"

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
    val avroSchema = new Schema.Parser().parse(avroSchemaStr)

    def rowToAvroBytes(r: Row): Array[Byte] = {
      val rec = new GenericData.Record(avroSchema)
      rec.put("order_id", r.getAs[Int]("order_id"))
      rec.put("customer_id", r.getAs[Int]("customer_id"))
      rec.put("amount", r.getAs[Double]("amount"))
      val ts = r.getAs[java.sql.Timestamp]("created_at")
      rec.put("created_at", if (ts != null) ts.toString else "")
      val writer = new GenericDatumWriter[GenericData.Record](avroSchema)
      val out = new ByteArrayOutputStream()
      val encoder = EncoderFactory.get().binaryEncoder(out, null)
      writer.write(rec, encoder)
      encoder.flush()
      out.toByteArray
    }

    // simple S3 offset read/write helpers
    import scala.util.Try
    val hconf = spark.sparkContext.hadoopConfiguration
    def readLastId(): Long = {
      val fs = org.apache.hadoop.fs.FileSystem.get(hconf)
      val p = new org.apache.hadoop.fs.Path(offsetPath)
      if (fs.exists(p)) {
        val is = fs.open(p)
        val s = scala.io.Source.fromInputStream(is).mkString.trim
        is.close()
        Try(s.toLong).getOrElse(0L)
      } else 0L
    }
    def writeLastId(id: Long): Unit = {
      val fs = org.apache.hadoop.fs.FileSystem.get(hconf)
      val p = new org.apache.hadoop.fs.Path(offsetPath)
      val os = fs.create(p, true)
      os.write(id.toString.getBytes("UTF-8"))
      os.close()
    }

    val rateDf = spark.readStream.format("rate").option("rowsPerSecond", 0.2).load()

    val q = rateDf.writeStream.foreachBatch { (batchDf: DataFrame, batchId: Long) =>
      val lastId = readLastId()
      val sqlQ = s"(SELECT * FROM new_orders WHERE order_id > $lastId ORDER BY order_id ASC) AS t"
      val newRows = spark.read.jdbc(jdbcUrl, sqlQ, jdbcProps)

      if (!newRows.rdd.isEmpty) {
        val rows = newRows.collect() // small micro-batches; careful with large volumes
        val maxId = rows.map(r => r.getAs[Int]("order_id")).max

        val kv = rows.map { r =>
          val bytes = rowToAvroBytes(r)
          val b64 = java.util.Base64.getEncoder.encodeToString(bytes)
          (r.getAs[Int]("order_id").toString, b64)
        }

        val avroDf = spark.createDataFrame(kv.toSeq).toDF("key", "value")
        avroDf.selectExpr("CAST(key AS STRING) as key", "CAST(value AS STRING) as value")
          .write
          .format("kafka")
          .option("kafka.bootstrap.servers", kafkaBootstrap)
          .option("topic", "orders_avro_topic")
          .save()

        writeLastId(maxId.toLong)
      }
    }.trigger(org.apache.spark.sql.streaming.Trigger.ProcessingTime("5 seconds")).start()

    q.awaitTermination()
  }
}
