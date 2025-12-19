package com.smartfleet.pipeline2

import org.apache.spark.sql.{SparkSession, Dataset}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.streaming.Trigger
import com.smartfleet.common.models._
import com.smartfleet.common.utils.ProtobufConverter
import com.smartfleet.common.database.DynamoDatabase
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import com.typesafe.config.ConfigFactory
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

/**
 * FINAL Pipeline2 — Single file, production stable.
 * Kafka → DynamoDB → S3 (80KB protobuf split)
 */
object Pipeline2App {

  // ---- FILE SIZE TARGET ----
  val MAX_FILE_BYTES = 80 * 1024         // hard limit
  val EVENT_EST_BYTES = 180              // approx per-event protobuf size
  val HEADER_EST_BYTES = 120             // small batch header size

  def main(args: Array[String]): Unit = {
    println("� Pipelinie2 Final Version")
    println("📦 Max file size = 80KB")
    println("📁 S3 Prefix = telemetry/enriched")

    val spark = SparkSession.builder()
      .master("local[*]")
      .appName("Pipeline2-Final")
      .config("spark.sql.adaptive.enabled", "true")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()

    import spark.implicits._

    val config = ConfigFactory.load()
    val kafkaBootstrap = config.getString("kafka.bootstrap-servers")
    val topic = config.getString("kafka.topics.telemetry")

    println(s"📡 Kafka: $kafkaBootstrap")
    println(s"📋 Topic: $topic")

    // -------- Schema --------
    val schema = new StructType()
      .add("vehicle_id", StringType)
      .add("fleet_id", IntegerType)
      .add("speed_kmh", DoubleType)
      .add("fuel_level_percent", DoubleType)
      .add("engine_temp_c", DoubleType)
      .add("gps_lat", DoubleType)
      .add("gps_long", DoubleType)
      .add("device_status", StringType)
      .add("timestamp", LongType)

    val streamDF = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaBootstrap)
      .option("subscribe", topic)
      .option("startingOffsets", "latest")
      .load()

    val telemetryDS = streamDF
      .select(from_json(col("value").cast("string"), schema).as("data"))
      .select("data.*")
      .as[VehicleTelemetryEvent]

    // -------- DynamoDB --------
    val dynamo = new DynamoDatabase(config)
    dynamo.initializeTable()

    // -------- S3 CLIENT --------
    val s3 = S3Client.builder()
      .region(Region.of("eu-north-1"))
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(
            config.getString("aws.access-key"),
            config.getString("aws.secret-key")
          )
        )
      ).build()

    val bucket = config.getString("s3.bucket-name")
    val s3Prefix = "telemetry/enriched"   // final clean path

    val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val hourFmt = DateTimeFormatter.ofPattern("HH")

    // -------- STREAMING FOREACH BATCH --------
    telemetryDS.writeStream
      .foreachBatch { (batch: Dataset[VehicleTelemetryEvent], batchId: Long) =>
        val events = batch.collect().toList
        
        if (events.isEmpty) {
          println(s"📭 Batch $batchId empty")
        } else {
          println(s"\n🔄 Batch $batchId — ${events.size} events")

          // ----- Insert to DynamoDB -----
          // Deduplicate by vehicle_id + timestamp to avoid DynamoDB key conflicts
          val uniqueEvents = events.groupBy(e => (e.vehicle_id, e.timestamp)).values.map(_.head).toList
          
          val ddbRecords = uniqueEvents.map { e =>
            DynamoTelemetryRecord(
              fleet_id = e.fleet_id,
              timestamp = e.timestamp,
              vehicle_id = e.vehicle_id,
              speed_kmh = e.speed_kmh,
              fuel_level_percent = e.fuel_level_percent,
              engine_temp_c = e.engine_temp_c,
              gps_lat = e.gps_lat,
              gps_long = e.gps_long,
              device_status = e.device_status
            )
          }

          try {
            dynamo.batchInsertTelemetryRecords(ddbRecords)
            println(s"✅ DynamoDB inserted ${ddbRecords.size} (deduplicated from ${events.size})")
          } catch { 
            case e: Exception =>
              println(s"❌ DynamoDB error: ${e.getMessage}")
          }

          // ----- Enrich events -----
          val enriched = events.map { e =>
            val (isAnomaly, anomalyType) = AnomalyDetector.detectAnomalies(e)
            e.toEnrichedEvent(isAnomaly, anomalyType)
          }

          // ----- Use timestamp of first event -----
          val ts = enriched.head.timestamp
          val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("UTC"))
          val date = dateFmt.format(zdt)
          val hour = hourFmt.format(zdt)

          println(s"📅 S3 Partition: date=$date/hour=$hour")

          // ----- Split into smaller files -----
          val partitionedFiles = splitFiles(enriched)
          println(s"📦 Total parts: ${partitionedFiles.size}")

          // ----- Upload each part -----
          partitionedFiles.zipWithIndex.foreach { case (chunk, idx) =>
            val bytes = ProtobufConverter.toBatchProtobufBytes(chunk, s"batch-$batchId-$idx")
            val sizeKB = bytes.length / 1024
            val key = s"$s3Prefix/date=$date/hour=$hour/part-$batchId-$idx.pb"

            println(s"   ⬆ Uploading part-$idx ($sizeKB KB) → $key")

            try {
              val req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/x-protobuf")
                .build()
              
              s3.putObject(req, RequestBody.fromBytes(bytes))
              println("   ✅ OK")
            } catch {
              case e: Exception =>
                println(s"   ❌ Upload failed: ${e.getMessage}")
            }
          }
        }
      }
      .option("checkpointLocation", s"/tmp/pipeline2-checkpoint-${System.currentTimeMillis()}")
      .start()

    println("🚀 Pipeline2 Final Running")
    spark.streams.awaitAnyTermination()
  }

  // =====================================================================================
  // SPLIT LIST OF EVENTS INTO MULTIPLE ~60KB FILES (MAX 80KB)
  // =====================================================================================
  def splitFiles(events: List[EnrichedTelemetryEvent]): List[List[EnrichedTelemetryEvent]] = {
    val result = scala.collection.mutable.ListBuffer[List[EnrichedTelemetryEvent]]()
    var current = scala.collection.mutable.ListBuffer[EnrichedTelemetryEvent]()
    var size = HEADER_EST_BYTES

    events.foreach { e =>
      val est = EVENT_EST_BYTES
      
      if (size + est > MAX_FILE_BYTES && current.nonEmpty) {
        result += current.toList
        current.clear()
        size = HEADER_EST_BYTES
      }
      
      current += e
      size += est
    }

    if (current.nonEmpty) {
      result += current.toList
    }

    result.toList
  }
}