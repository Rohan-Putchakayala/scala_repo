package com.smartfleet.batch

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
import software.amazon.awssdk.core.ResponseInputStream
import java.time.{LocalDate, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.collection.JavaConverters._

/**
 * Pipeline C - Daily Fleet Summary Batch Job
 * Runs once per day to process all protobuf readings and generate fleet summaries
 */
object PipelineCApp {

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val targetDate = if (args.length > 0) args(0) else LocalDate.now().minusDays(1).toString // Yesterday by default
    
    println(s"Starting Pipeline C - Daily Fleet Summary for date: $targetDate")
    
    // Set system property to work around Java security issue
    System.setProperty("HADOOP_USER_NAME", "spark")
    
    val spark = SparkSession.builder()
      .appName("SmartFleet-PipelineC-DailyFleetSummary")
      .master("local[*]")
      .config("spark.hadoop.fs.defaultFS", "file:///")
      .config("spark.sql.warehouse.dir", "/tmp/spark-warehouse")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.sql.adaptive.enabled", "true")
      .config("spark.hadoop.security.authentication", "simple")
      .config("spark.hadoop.security.authorization", "false")
      .getOrCreate()

    import spark.implicits._

    try {
      // Initialize S3 client
      val s3Region = Region.of(config.getString("s3.region"))
      val s3Bucket = config.getString("s3.bucket-name")
      val s3PathPrefix = config.getString("s3.paths.raw")
      
      val awsCredentials = software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
        config.getString("aws.access-key"),
        config.getString("aws.secret-key")
      )
      val s3Client = S3Client.builder()
        .region(s3Region)
        .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(awsCredentials))
        .build()

      // Initialize MySQL database
      val mysqlDb = new MySQLDatabase(config)
      mysqlDb.initializeTables()

      // 1. Read all protobuf files for the target date from S3
      println(s"Reading protobuf files for date: $targetDate")
      val protobufData = readProtobufDataForDate(spark, s3Client, s3Bucket, s3PathPrefix, targetDate)
      
      if (protobufData.isEmpty) {
        println(s"No protobuf data found for date: $targetDate")
        return
      }

      // 2. Load fleet and vehicle data from MySQL
      println("Loading fleet and vehicle data from MySQL")
      val fleetData = mysqlDb.getAllFleets()
      val vehicleData = mysqlDb.getAllVehicles()
      
      val fleetDF = spark.createDataFrame(fleetData).toDF()
      val vehicleDF = spark.createDataFrame(vehicleData).toDF()

      // 3. Process telemetry data and calculate fleet summaries
      println("Processing telemetry data and calculating fleet summaries")
      val telemetryDF = spark.createDataFrame(protobufData).toDF()
      
      val fleetSummaries = calculateFleetSummaries(spark, telemetryDF, fleetDF, vehicleDF, targetDate)
      
      // 4. Write summaries to MySQL
      println(s"Writing ${fleetSummaries.size} fleet summaries to MySQL")
      fleetSummaries.foreach { summary =>
        mysqlDb.insertFleetDailySummary(summary)
      }
      
      println(s"Pipeline C completed successfully for date: $targetDate")
      
    } catch {
      case e: Exception =>
        println(s"Pipeline C failed: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      spark.stop()
    }
  }

  private def readProtobufDataForDate(
    spark: SparkSession, 
    s3Client: S3Client, 
    bucket: String, 
    pathPrefix: String, 
    date: String
  ): List[EnrichedTelemetryEvent] = {
    
    val datePrefix = s"$pathPrefix/date=$date/"
    
    // List all objects for the date
    val listRequest = ListObjectsV2Request.builder()
      .bucket(bucket)
      .prefix(datePrefix)
      .build()
    
    val objects = s3Client.listObjectsV2(listRequest).contents().asScala.toList
    
    println(s"Found ${objects.size} protobuf files for date: $date")
    
    // Read and parse each protobuf file
    val allEvents = objects.flatMap { obj =>
      try {
        val getRequest = GetObjectRequest.builder()
          .bucket(bucket)
          .key(obj.key())
          .build()
        
        val inputStream = s3Client.getObject(getRequest)
        val bytes = inputStream.readAllBytes()
        inputStream.close()
        
        // Parse protobuf bytes back to events
        ProtobufConverter.fromBatchProtobufBytes(bytes)
      } catch {
        case e: Exception =>
          println(s"Error reading protobuf file ${obj.key()}: ${e.getMessage}")
          List.empty[EnrichedTelemetryEvent]
      }
    }
    
    println(s"Loaded ${allEvents.size} telemetry events for date: $date")
    allEvents
  }

  private def calculateFleetSummaries(
    spark: SparkSession,
    telemetryDF: DataFrame,
    fleetDF: DataFrame,
    vehicleDF: DataFrame,
    date: String
  ): List[FleetDailySummary] = {
    
    import spark.implicits._
    
    // Join telemetry with vehicle data to get fleet info
    val enrichedTelemetry = telemetryDF
      .join(vehicleDF, "vehicle_id")
      .select(
        col("fleet_id"),
        col("vehicle_id"),
        col("speed_kmh"),
        col("fuel_level_percent"),
        col("is_anomaly"),
        col("timestamp")
      )
    
    // Calculate distance traveled (simplified: speed * time intervals)
    val telemetryWithDistance = enrichedTelemetry
      .withColumn("distance_km", col("speed_kmh") * 0.25 / 60) // Assuming 15-second intervals
    
    // Aggregate by fleet
    val fleetAggregates = telemetryWithDistance
      .groupBy("fleet_id")
      .agg(
        sum("distance_km").alias("total_distance_km"),
        avg("speed_kmh").alias("avg_speed"),
        // Estimate fuel consumed based on distance and vehicle efficiency
        (sum("distance_km") * 0.08).alias("fuel_consumed_liters"), // 8L/100km average
        sum(when(col("is_anomaly"), 1).otherwise(0)).alias("anomaly_count")
      )
    
    // Convert to FleetDailySummary objects
    fleetAggregates.collect().map { row =>
      FleetDailySummary(
        record_id = UUID.randomUUID().toString,
        fleet_id = row.getAs[Int]("fleet_id"),
        total_distance_km = row.getAs[Double]("total_distance_km"),
        avg_speed = row.getAs[Double]("avg_speed"),
        fuel_consumed_liters = row.getAs[Double]("fuel_consumed_liters"),
        anomaly_count = row.getAs[Long]("anomaly_count").toInt,
        record_date = date,
        generated_at = java.time.Instant.now()
      )
    }.toList
  }
}