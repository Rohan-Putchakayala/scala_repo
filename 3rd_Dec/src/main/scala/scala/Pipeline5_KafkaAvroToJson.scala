package scala

import com.typesafe.config.ConfigFactory
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.avro.functions.from_avro
import org.apache.hadoop.fs.{FileSystem, Path}

object Pipeline5_KafkaAvroToJson {

  def main(args: Array[String]): Unit = {

    val conf = ConfigFactory.load()

    // ---------- Kafka ----------
    val kafka = conf.getConfig("kafka")
    val kafkaBootstrap = kafka.getString("bootstrap.servers")
    val kafkaTopic = kafka.getString("topic")

    // ---------- S3 ----------
    val s3Bucket = conf.getString("s3.bucket")
    val s3PathRaw = conf.getString("s3.path")
    val s3Path = s"s3a://$s3Bucket/${s3PathRaw.stripSuffix("/")}"
    val checkpointS3 = s"$s3Path/spark_metadata"
    val localCheckpoint = "/tmp/pipeline5_checkpoint"

    val pollIntervalSec = conf.getInt("app.pollIntervalSec")

    // ---------- Spark ----------
    val spark = SparkSession.builder()
      .appName("Kafka (Avro) -> JSON on S3")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    // ---------- S3 Hadoop config ----------
    val hadoopConf = spark.sparkContext.hadoopConfiguration
    hadoopConf.set("fs.s3a.access.key", conf.getString("s3.accesskey"))
    hadoopConf.set("fs.s3a.secret.key", conf.getString("s3.secretkey"))
    hadoopConf.set("fs.s3a.endpoint", conf.getString("s3.endpoint"))
    hadoopConf.set("fs.s3a.path.style.access", conf.getString("s3.pathStyleAccess"))
    hadoopConf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
    hadoopConf.set("fs.s3a.connection.ssl.enabled", "true")
    hadoopConf.set("fs.s3a.buffer.dir", conf.getString("s3.bufferDir"))

    // ---------- Test S3 access ----------
    try {
      val fs = FileSystem.get(hadoopConf)
      val s3TestPath = new Path(s3Path)
      println(s"Testing S3 bucket access: $s3TestPath")
      println(s"Exists? ${fs.exists(s3TestPath)}")
    } catch {
      case ex: Throwable =>
        println(s"[WARN] Unable to access S3: ${ex.getMessage}")
    }

    // ---------- Kafka stream ----------
    val kafkaDF = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaBootstrap)
      .option("subscribe", kafkaTopic)
      .option("startingOffsets", "earliest")
      .option("failOnDataLoss", "false")
      .load()

    // ---------- Convert Avro to JSON ----------
    val avroSchemaJson: String =
      """
        |{
        |  "type": "record",
        |  "name": "OrderRecord",
        |  "namespace": "com.retail",
        |  "fields": [
        |    { "name": "order_id", "type": "int" },
        |    { "name": "customer_id", "type": "int" },
        |    { "name": "amount", "type": "double" },
        |    { "name": "created_at", "type": "string" }
        |  ]
        |}
        |""".stripMargin

    val jsonDF = kafkaDF.select(
        from_avro($"value", avroSchemaJson).as("data")
      ).select("data.*")
      .withColumn("processing_time", current_timestamp())

    jsonDF.printSchema()

    // ---------- Write to S3 as JSON ----------
    val query = jsonDF.writeStream
      .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
        val count = batchDF.count()
        println(s"\n=== Batch $batchId at ${java.time.LocalDateTime.now()} ===")
        println(s"Records in batch: $count")

        if (count > 0) {
          batchDF
            .coalesce(1) // optional: reduce small files
            .write
            .mode("append")
            .json(s3Path)

          println(s"✓ Successfully wrote $count records to S3 at $s3Path")
        } else {
          println("No new records in this batch")
        }
      }
      .option("checkpointLocation", checkpointS3)
      .trigger(Trigger.ProcessingTime(s"${pollIntervalSec} seconds"))
      .start()

    query.awaitTermination()
  }
}
