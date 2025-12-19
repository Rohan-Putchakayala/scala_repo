package com.smartfleet.pipeline3

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import com.smartfleet.common.models.{EnrichedTelemetryEvent, FleetDailySummary}
import com.smartfleet.common.utils.ProtobufConverter
import com.smartfleet.common.database.MySQLDatabase
import com.typesafe.config.ConfigFactory
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{ListObjectsV2Request, GetObjectRequest}
import java.time.LocalDate
import scala.jdk.CollectionConverters._
import java.util.UUID

/**
 * Pipeline3 - Daily Fleet Summary Batch Job (Pipeline C)
 */
object Pipeline3App {

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val targetDate = if (args.length > 0) args(0) else LocalDate.now().minusDays(1).toString

    println(s"=== Pipeline3 - Daily Fleet Summary Batch Job ===")
    println(s"Processing date: $targetDate")

    System.setProperty("HADOOP_USER_NAME", "spark")

    val spark = SparkSession.builder()
      .appName("SmartFleet-Pipeline3-DailyFleetSummary")
      .master("local[*]")
      .config("spark.hadoop.fs.defaultFS", "file:///")
      .config("spark.sql.warehouse.dir", "/tmp/spark-warehouse")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.sql.adaptive.enabled", "true")
      .getOrCreate()

    import spark.implicits._

    try {
      // Initialize S3
      val s3Region = Region.of(config.getString("s3.region"))
      val s3Bucket = config.getString("s3.bucket-name")
      val s3PathPrefix = config.getString("s3.paths.raw")

      val awsCredentials = software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
        config.getString("aws.access-key"),
        config.getString("aws.secret-key")
      )

      val s3Client = S3Client.builder()
        .region(s3Region)
        .credentialsProvider(
          software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(awsCredentials)
        )
        .build()

      // Initialize MySQL
      val mysqlDb = new MySQLDatabase(config)
      mysqlDb.initializeTables()

      // Step 1: Read Protobuf data
      println(s"Step 1: Reading protobuf files for date: $targetDate")
      val s3PathPrefixCorrected = "telemetry/enriched"  // Use the correct path where Pipeline2 writes
      val protobufData = readProtobufDataForDate(spark, s3Client, s3Bucket, s3PathPrefixCorrected, targetDate)

      if (protobufData.isEmpty) {
        println(s"No protobuf data found for date: $targetDate. Exiting.")
        return
      }

      // Step 2: MySQL data
      println("Step 2: Loading fleet and vehicle data from MySQL")
      val fleetData = mysqlDb.getAllFleets()
      val vehicleData = mysqlDb.getAllVehicles()

      val fleetDF = spark.createDataFrame(fleetData).toDF()
      val vehicleDF = spark.createDataFrame(vehicleData).toDF()

      // Step 3: Calculations
      println("Step 3: Processing telemetry data and calculating fleet summaries")
      val telemetryDF = spark.createDataFrame(protobufData).toDF()

      val fleetSummaries =
        calculateFleetSummaries(spark, telemetryDF, fleetDF, vehicleDF, targetDate)

      // Step 4: Write to MySQL
      println(s"Step 4: Writing ${fleetSummaries.size} fleet summaries to MySQL")
      fleetSummaries.foreach { summary =>
        try {
          mysqlDb.insertFleetDailySummary(summary)
          println(s"  ✓ Fleet ${summary.fleet_id}: ${summary.total_distance_km}km, anomalies ${summary.anomaly_count}")
        } catch {
          case e: Exception =>
            println(s"  ✗ Failed to insert summary for fleet ${summary.fleet_id}: ${e.getMessage}")
        }
      }

      println(s"=== Pipeline3 completed successfully for date: $targetDate ===")

    } catch {
      case e: Exception =>
        println(s"Pipeline3 failed: ${e.getMessage}")
        e.printStackTrace()

    } finally {
      // 🔥 Keep Spark UI alive indefinitely
      println("\n🔥 Spark job finished — keeping Spark UI open for debugging.")
      println("   Visit the Spark UI in browser and press CTRL + C to stop this job.\n")

      Thread.sleep(Long.MaxValue)

      spark.stop()
    }
  }

  // --- READ PROTOBUF FILES ---
  private def readProtobufDataForDate(
                                       spark: SparkSession,
                                       s3Client: S3Client,
                                       bucket: String,
                                       pathPrefix: String,
                                       date: String
                                     ): List[EnrichedTelemetryEvent] = {

    val datePrefix = s"$pathPrefix/date=$date/"

    try {
      val listRequest = ListObjectsV2Request.builder()
        .bucket(bucket)
        .prefix(datePrefix)
        .build()

      val objects = s3Client.listObjectsV2(listRequest).contents().asScala.toList
        .filter(_.key().endsWith(".pb"))  // Only protobuf files

      println(s"Found ${objects.size} protobuf files for date: $date")
      
      if (objects.isEmpty) {
        println(s"No protobuf files found in S3 path: $datePrefix")
        println("Available S3 objects in telemetry/enriched/:")
        
        // List what's actually available for debugging
        val debugListRequest = ListObjectsV2Request.builder()
          .bucket(bucket)
          .prefix("telemetry/enriched/")
          .build()
        
        val debugObjects = s3Client.listObjectsV2(debugListRequest).contents().asScala.take(10)
        debugObjects.foreach(obj => println(s"  - ${obj.key()}"))
        
        return List.empty[EnrichedTelemetryEvent]
      }

      val allEvents = objects.flatMap { obj =>
        try {
          println(s"Reading protobuf file: ${obj.key()}")
          val getRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(obj.key())
            .build()

          val inputStream = s3Client.getObject(getRequest)
          val bytes = inputStream.readAllBytes()
          inputStream.close()

          val events = ProtobufConverter.fromBatchProtobufBytes(bytes)
          println(s"  Loaded ${events.size} events from ${obj.key()}")
          events

        } catch {
          case e: Exception =>
            println(s"Error reading protobuf file ${obj.key()}: ${e.getMessage}")
            List.empty[EnrichedTelemetryEvent]
        }
      }

      println(s"Loaded ${allEvents.size} telemetry events for date: $date")
      allEvents

    } catch {
      case e: Exception =>
        println(s"Error listing S3 objects for date $date: ${e.getMessage}")
        List.empty[EnrichedTelemetryEvent]
    }
  }

  // --- CALCULATE FLEET SUMMARIES ---
  private def calculateFleetSummaries(
                                       spark: SparkSession,
                                       telemetryDF: DataFrame,
                                       fleetDF: DataFrame,
                                       vehicleDF: DataFrame,
                                       date: String
                                     ): List[FleetDailySummary] = {

    import spark.implicits._

    try {
      val telemetryRen = telemetryDF.withColumnRenamed("fleet_id", "telemetry_fleet_id")
      val vehicleRen   = vehicleDF.withColumnRenamed("fleet_id", "vehicle_fleet_id")

      val joined = telemetryRen.join(vehicleRen, Seq("vehicle_id"), "left")

      val enrichedTelemetry = joined.select(
        coalesce(col("vehicle_fleet_id"), col("telemetry_fleet_id")).alias("fleet_id"),
        col("vehicle_id"),
        col("speed_kmh"),
        col("fuel_level_percent"),
        col("is_anomaly"),
        col("timestamp")
      )

      val telemetryWithDistance = enrichedTelemetry.withColumn(
        "distance_km",
        col("speed_kmh") * 15.0 / 3600.0
      )

      val fleetAggregates = telemetryWithDistance.groupBy("fleet_id").agg(
        sum("distance_km").alias("total_distance_km"),
        avg("speed_kmh").alias("avg_speed"),
        (sum("distance_km") * 0.08).alias("fuel_consumed_liters"),
        sum(when(col("is_anomaly"), 1).otherwise(0)).alias("anomaly_count"),
        count("*").alias("total_readings")
      )

      val summaries = fleetAggregates.collect().map { row =>
        FleetDailySummary(
          record_id = UUID.randomUUID().toString,
          fleet_id = row.getAs[Int]("fleet_id"),
          total_distance_km = math.round(row.getAs[Double]("total_distance_km") * 100.0) / 100.0,
          avg_speed = math.round(row.getAs[Double]("avg_speed") * 100.0) / 100.0,
          fuel_consumed_liters = math.round(row.getAs[Double]("fuel_consumed_liters") * 100.0) / 100.0,
          anomaly_count = row.getAs[Long]("anomaly_count").toInt,
          record_date = date,
          generated_at = java.time.Instant.now()
        )
      }.toList

      println(s"Generated summaries for ${summaries.size} fleets")
      summaries.foreach { s =>
        println(s"  Fleet ${s.fleet_id}: ${s.total_distance_km}km, avg ${s.avg_speed}km/h, ${s.anomaly_count} anomalies")
      }

      summaries

    } catch {
      case e: Exception =>
        println(s"Error calculating fleet summaries: ${e.getMessage}")
        e.printStackTrace()
        List.empty[FleetDailySummary]
    }
  }

}
