package com.smartfleet.streaming

import org.apache.spark.sql.{Encoders, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{GroupState, GroupStateTimeout, OutputMode}
import org.apache.spark.sql.types._
import com.smartfleet.common.models.{VehicleTelemetryEvent, EnrichedTelemetryEvent, AnomalyDetector, DynamoTelemetryRecord}
import com.smartfleet.common.database.DynamoDatabase
import com.smartfleet.common.utils.ProtobufConverter
import com.typesafe.config.{Config, ConfigFactory}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.core.sync.RequestBody
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

object StreamingPipelineApp {
  case class VehicleState(prevSpeed: Option[Double])

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val appName = if (config.hasPath("spark.app-name")) config.getString("spark.app-name") else "SmartFleetStreaming"
    val master = if (config.hasPath("spark.master")) config.getString("spark.master") else "local[*]"

    val spark = SparkSession.builder()
      .appName(appName)
      .master(master)
      .config("spark.ui.port", "4050")
      .getOrCreate()

    import spark.implicits._

    val kafkaBootstrap = config.getString("kafka.bootstrap-servers")
    val telemetryTopic = config.getString("kafka.topics.telemetry")

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

    val raw = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaBootstrap)
      .option("subscribe", telemetryTopic)
      .option("startingOffsets", "latest")
      .load()

    val telemetryDF = raw.select(from_json(col("value").cast("string"), schema).as("data")).select("data.*")
    val telemetryDS = telemetryDF.as[VehicleTelemetryEvent]

    val stateEncoder = Encoders.product[VehicleState]
    val eventEncoder = Encoders.product[EnrichedTelemetryEvent]

    val updateFunc = (key: String, inputs: Iterator[VehicleTelemetryEvent], state: GroupState[VehicleState]) => {
      val s = if (state.exists) state.get else VehicleState(None)
      val out = inputs.toList
        .filter(AnomalyDetector.validateFields) // Field validation
        .sortBy(_.timestamp)
        .map { ev =>
          val (isAnom, typ) = AnomalyDetector.detectAnomalies(ev, s.prevSpeed)
          val enriched = ev.toEnrichedEvent(isAnom, typ)
          state.update(VehicleState(Some(ev.speed_kmh)))
          enriched
        }
      out.iterator
    }

    val enrichedDS = telemetryDS
      .groupByKey(_.vehicle_id)
      .flatMapGroupsWithState(OutputMode.Append(), GroupStateTimeout.NoTimeout())(updateFunc)(stateEncoder, eventEncoder)

    val dynamoDb = new DynamoDatabase(config)
    try { dynamoDb.initializeTable() } catch { case _: Throwable => () }
    val s3Region = Region.of(config.getString("s3.region"))
    val s3Bucket = config.getString("s3.bucket-name")
    val s3PathPrefix = config.getString("s3.paths.raw")
    val s3Client = S3Client.builder().region(s3Region).build()

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val hourFormatter = DateTimeFormatter.ofPattern("HH")

    val query = enrichedDS.writeStream
      .outputMode("append")
      .foreachBatch { (batch: org.apache.spark.sql.Dataset[EnrichedTelemetryEvent], batchId: Long) =>
        val records = batch.collect().toList

        val dynamoRecords = records.map { e =>
          DynamoTelemetryRecord(
            fleet_id = e.fleet_id,
            timestamp = e.timestamp * 1000 + scala.util.Random.nextInt(1000), // FIX: unique SK
            vehicle_id = e.vehicle_id,
            speed_kmh = e.speed_kmh,
            fuel_level_percent = e.fuel_level_percent,
            engine_temp_c = e.engine_temp_c,
            gps_lat = e.gps_lat,
            gps_long = e.gps_long,
            device_status = e.device_status
          )
        }

        try { dynamoDb.batchInsertTelemetryRecords(dynamoRecords) } catch { case _: Throwable => () }
        val fleets = records.map(_.fleet_id).distinct
        fleets.foreach { fid =>
          try { dynamoDb.retainLatestPerFleet(fid, 50) } catch { case _: Throwable => () }
        }
        // Write enriched data to S3 as Protobuf
        if (records.nonEmpty) {
          val anyTs = records.head.timestamp
          val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(anyTs), ZoneId.of("UTC"))
          val date = dateFormatter.format(zdt)
          val hour = hourFormatter.format(zdt)
          val key = s"$s3PathPrefix/date=$date/hour=$hour/part-$batchId.protobuf"
          
          // Convert to protobuf format
          val protobufBytes = ProtobufConverter.toBatchProtobufBytes(records, s"batch-$batchId")
          val put = PutObjectRequest.builder()
            .bucket(s3Bucket)
            .key(key)
            .contentType("application/x-protobuf")
            .build()
          
          try { 
            s3Client.putObject(put, RequestBody.fromBytes(protobufBytes)) 
          } catch { 
            case _: Throwable => () 
          }
        }
        ()
      }
      .option("checkpointLocation", config.getString("spark.streaming.checkpoint-location"))
      .start()

    query.awaitTermination()
    spark.stop()
  }
}
