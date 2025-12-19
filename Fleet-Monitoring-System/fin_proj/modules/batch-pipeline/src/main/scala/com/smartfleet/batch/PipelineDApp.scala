package com.smartfleet.batch

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import com.smartfleet.common.models.{EnrichedTelemetryEvent}
import com.smartfleet.common.utils.ProtobufConverter
import com.smartfleet.common.database.MySQLDatabase
import com.typesafe.config.ConfigFactory
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{ListObjectsV2Request, GetObjectRequest, PutObjectRequest}
import software.amazon.awssdk.core.sync.RequestBody
import java.time.{LocalDate, ZoneId}
import java.time.format.DateTimeFormatter
import scala.collection.JavaConverters._

/**
 * Pipeline D - Daily Reports for Dashboard
 * Generates partitioned protobuf summary tables for dashboard APIs
 */
object PipelineDApp {

  // Report data models
  case class DailyAvgSpeedByFleet(
    fleet_id: Int,
    fleet_name: String,
    date: String,
    avg_speed: Double,
    max_speed: Double,
    min_speed: Double,
    vehicle_count: Int
  )

  case class DailyFuelConsumptionByFleet(
    fleet_id: Int,
    fleet_name: String,
    date: String,
    total_fuel_consumed: Double,
    avg_fuel_efficiency: Double, // km per liter
    total_distance: Double,
    vehicle_count: Int
  )

  case class DailyAnomalyCounts(
    fleet_id: Int,
    fleet_name: String,
    date: String,
    total_anomalies: Int,
    speeding_count: Int,
    engine_overheat_count: Int,
    sudden_stop_count: Int,
    low_fuel_count: Int,
    device_error_count: Int,
    anomaly_rate: Double // percentage
  )

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val targetDate = if (args.length > 0) args(0) else LocalDate.now().minusDays(1).toString // Yesterday by default
    
    println(s"Starting Pipeline D - Daily Reports for date: $targetDate")
    
    // Set system property to work around Java security issue
    System.setProperty("HADOOP_USER_NAME", "spark")
    
    val spark = SparkSession.builder()
      .appName("SmartFleet-PipelineD-DailyReports")
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

      // 1. Read protobuf data for the target date
      println(s"Reading protobuf files for date: $targetDate")
      val protobufData = readProtobufDataForDate(spark, s3Client, s3Bucket, s3PathPrefix, targetDate)
      
      if (protobufData.isEmpty) {
        println(s"No protobuf data found for date: $targetDate")
        return
      }

      // 2. Load fleet data from MySQL
      println("Loading fleet data from MySQL")
      val fleetData = mysqlDb.getAllFleets()
      val vehicleData = mysqlDb.getAllVehicles()
      
      val fleetDF = spark.createDataFrame(fleetData).toDF()
      val vehicleDF = spark.createDataFrame(vehicleData).toDF()

      // 3. Create telemetry DataFrame
      val telemetryDF = spark.createDataFrame(protobufData).toDF()

      // 4. Generate reports
      println("Generating daily reports...")
      
      // Report 1: Daily Average Speed by Fleet
      val avgSpeedReport = generateDailyAvgSpeedByFleet(spark, telemetryDF, fleetDF, vehicleDF, targetDate)
      writeReportToS3(s3Client, s3Bucket, "reports/daily_avg_speed_by_fleet", targetDate, avgSpeedReport)
      
      // Report 2: Daily Fuel Consumption by Fleet
      val fuelConsumptionReport = generateDailyFuelConsumptionByFleet(spark, telemetryDF, fleetDF, vehicleDF, targetDate)
      writeReportToS3(s3Client, s3Bucket, "reports/daily_fuel_consumption_by_fleet", targetDate, fuelConsumptionReport)
      
      // Report 3: Daily Anomaly Counts
      val anomalyReport = generateDailyAnomalyCounts(spark, telemetryDF, fleetDF, vehicleDF, targetDate)
      writeReportToS3(s3Client, s3Bucket, "reports/daily_anomaly_counts", targetDate, anomalyReport)
      
      println(s"Pipeline D completed successfully for date: $targetDate")
      
    } catch {
      case e: Exception =>
        println(s"Pipeline D failed: ${e.getMessage}")
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

  private def generateDailyAvgSpeedByFleet(
    spark: SparkSession,
    telemetryDF: DataFrame,
    fleetDF: DataFrame,
    vehicleDF: DataFrame,
    date: String
  ): List[DailyAvgSpeedByFleet] = {
    
    import spark.implicits._
    
    val enrichedTelemetry = telemetryDF
      .join(vehicleDF, "vehicle_id")
      .join(fleetDF, "fleet_id")
    
    val speedReport = enrichedTelemetry
      .groupBy("fleet_id", "fleet_name")
      .agg(
        avg("speed_kmh").alias("avg_speed"),
        max("speed_kmh").alias("max_speed"),
        min("speed_kmh").alias("min_speed"),
        countDistinct("vehicle_id").alias("vehicle_count")
      )
    
    speedReport.collect().map { row =>
      DailyAvgSpeedByFleet(
        fleet_id = row.getAs[Int]("fleet_id"),
        fleet_name = row.getAs[String]("fleet_name"),
        date = date,
        avg_speed = row.getAs[Double]("avg_speed"),
        max_speed = row.getAs[Double]("max_speed"),
        min_speed = row.getAs[Double]("min_speed"),
        vehicle_count = row.getAs[Long]("vehicle_count").toInt
      )
    }.toList
  }

  private def generateDailyFuelConsumptionByFleet(
    spark: SparkSession,
    telemetryDF: DataFrame,
    fleetDF: DataFrame,
    vehicleDF: DataFrame,
    date: String
  ): List[DailyFuelConsumptionByFleet] = {
    
    import spark.implicits._
    
    val enrichedTelemetry = telemetryDF
      .join(vehicleDF, "vehicle_id")
      .join(fleetDF, "fleet_id")
      .withColumn("distance_km", col("speed_kmh") * 0.25 / 60) // 15-second intervals
      .withColumn("fuel_consumed", col("distance_km") * 0.08) // 8L/100km average
    
    val fuelReport = enrichedTelemetry
      .groupBy("fleet_id", "fleet_name")
      .agg(
        sum("fuel_consumed").alias("total_fuel_consumed"),
        sum("distance_km").alias("total_distance"),
        countDistinct("vehicle_id").alias("vehicle_count")
      )
      .withColumn("avg_fuel_efficiency", 
        when(col("total_fuel_consumed") > 0, col("total_distance") / col("total_fuel_consumed"))
        .otherwise(0.0)
      )
    
    fuelReport.collect().map { row =>
      DailyFuelConsumptionByFleet(
        fleet_id = row.getAs[Int]("fleet_id"),
        fleet_name = row.getAs[String]("fleet_name"),
        date = date,
        total_fuel_consumed = row.getAs[Double]("total_fuel_consumed"),
        avg_fuel_efficiency = row.getAs[Double]("avg_fuel_efficiency"),
        total_distance = row.getAs[Double]("total_distance"),
        vehicle_count = row.getAs[Long]("vehicle_count").toInt
      )
    }.toList
  }

  private def generateDailyAnomalyCounts(
    spark: SparkSession,
    telemetryDF: DataFrame,
    fleetDF: DataFrame,
    vehicleDF: DataFrame,
    date: String
  ): List[DailyAnomalyCounts] = {
    
    import spark.implicits._
    
    val enrichedTelemetry = telemetryDF
      .join(vehicleDF, "vehicle_id")
      .join(fleetDF, "fleet_id")
    
    val anomalyReport = enrichedTelemetry
      .groupBy("fleet_id", "fleet_name")
      .agg(
        count("*").alias("total_records"),
        sum(when(col("is_anomaly"), 1).otherwise(0)).alias("total_anomalies"),
        sum(when(col("anomaly_type") === "SPEEDING", 1).otherwise(0)).alias("speeding_count"),
        sum(when(col("anomaly_type") === "ENGINE_OVERHEAT", 1).otherwise(0)).alias("engine_overheat_count"),
        sum(when(col("anomaly_type") === "SUDDEN_STOP", 1).otherwise(0)).alias("sudden_stop_count"),
        sum(when(col("anomaly_type") === "LOW_FUEL", 1).otherwise(0)).alias("low_fuel_count"),
        sum(when(col("anomaly_type") === "DEVICE_ERROR", 1).otherwise(0)).alias("device_error_count")
      )
      .withColumn("anomaly_rate", 
        when(col("total_records") > 0, col("total_anomalies") * 100.0 / col("total_records"))
        .otherwise(0.0)
      )
    
    anomalyReport.collect().map { row =>
      DailyAnomalyCounts(
        fleet_id = row.getAs[Int]("fleet_id"),
        fleet_name = row.getAs[String]("fleet_name"),
        date = date,
        total_anomalies = row.getAs[Long]("total_anomalies").toInt,
        speeding_count = row.getAs[Long]("speeding_count").toInt,
        engine_overheat_count = row.getAs[Long]("engine_overheat_count").toInt,
        sudden_stop_count = row.getAs[Long]("sudden_stop_count").toInt,
        low_fuel_count = row.getAs[Long]("low_fuel_count").toInt,
        device_error_count = row.getAs[Long]("device_error_count").toInt,
        anomaly_rate = row.getAs[Double]("anomaly_rate")
      )
    }.toList
  }

  private def writeReportToS3[T](
    s3Client: S3Client,
    bucket: String,
    reportPath: String,
    date: String,
    data: List[T]
  ): Unit = {
    
    // Convert data to protobuf format (simplified - using JSON for now)
    import spray.json._
    import DefaultJsonProtocol._
    
    val jsonData = data match {
      case avgSpeedData: List[DailyAvgSpeedByFleet] =>
        implicit val format = jsonFormat7(DailyAvgSpeedByFleet)
        avgSpeedData.toJson.compactPrint
      case fuelData: List[DailyFuelConsumptionByFleet] =>
        implicit val format = jsonFormat7(DailyFuelConsumptionByFleet)
        fuelData.toJson.compactPrint
      case anomalyData: List[DailyAnomalyCounts] =>
        implicit val format = jsonFormat10(DailyAnomalyCounts)
        anomalyData.toJson.compactPrint
      case _ =>
        data.toString
    }
    
    val s3Key = s"$reportPath/date=$date/report.json"
    
    val putRequest = PutObjectRequest.builder()
      .bucket(bucket)
      .key(s3Key)
      .contentType("application/json")
      .build()
    
    try {
      s3Client.putObject(putRequest, RequestBody.fromString(jsonData))
      println(s"Successfully wrote report to S3: $s3Key (${data.size} records)")
    } catch {
      case e: Exception =>
        println(s"Failed to write report to S3: ${e.getMessage}")
    }
  }
}