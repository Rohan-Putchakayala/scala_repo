package com.smartfleet.pipeline4

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import com.smartfleet.common.models.{EnrichedTelemetryEvent, DailyAvgSpeedByFleet, DailyFuelConsumptionByFleet, DailyAnomalyCounts}
import com.smartfleet.common.database.MySQLDatabase
import com.smartfleet.common.utils.ProtobufConverter
import com.typesafe.config.ConfigFactory
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.core.sync.RequestBody
import java.time.LocalDate
import scala.jdk.CollectionConverters._

object Pipeline4App {

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val date = if (args.nonEmpty) args(0) else LocalDate.now().minusDays(1).toString

    println(s"=== Pipeline4 — Daily Dashboard Reports (PROTOBUF) ===")
    println(s"Date: $date")

    System.setProperty("HADOOP_USER_NAME", "spark")

    val spark = SparkSession.builder()
      .appName("Pipeline4")
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
      val mysql = new MySQLDatabase(config)
      val region = Region.of(config.getString("s3.region"))
      val bucket = config.getString("s3.bucket-name")
      val rawPrefix = config.getString("s3.paths.raw")

      val s3 = S3Client.builder()
        .region(region)
        .credentialsProvider(
          software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
            software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
              config.getString("aws.access-key"),
              config.getString("aws.secret-key")
            )
          )
        ).build()

      // ---- Load telemetry ----
      val correctedPrefix = "telemetry/enriched"  // Use the correct path where Pipeline2 writes
      val telemetry = ProtobufConverter.readProtobufDataForDate(s3, bucket, correctedPrefix, date)
      if (telemetry.isEmpty) {
        println(s"No telemetry found for $date in path: $correctedPrefix/date=$date/")
        println("This might be because:")
        println("1. Pipeline2 hasn't written data for this date yet")
        println("2. The date format is incorrect")
        println("3. S3 permissions issue")
        return
      }

      val telemetryDF = spark.createDataFrame(telemetry).toDF()

      // ---- Load metadata ----
      val fleetDF = spark.createDataFrame(mysql.getAllFleets()).toDF()
      val vehicleDF = spark.createDataFrame(mysql.getAllVehicles()).toDF()

      // ---- Build reports ----
      val speedReport = generateSpeedReport(spark, telemetryDF, fleetDF, vehicleDF, date)
      val fuelReport = generateFuelReport(spark, telemetryDF, fleetDF, vehicleDF, date)
      val anomalyReport = generateAnomalyReport(spark, telemetryDF, fleetDF, vehicleDF, date)

      // ---- Serialize to .pb using our custom protobuf converter ----
      writePB(s3, bucket,
        s"reports/daily_avg_speed_by_fleet/date=$date/report.pb",
        ProtobufConverter.toAvgSpeedPB(speedReport.toList))

      writePB(s3, bucket,
        s"reports/daily_fuel_consumption_by_fleet/date=$date/report.pb",
        ProtobufConverter.toFuelPB(fuelReport.toList))

      writePB(s3, bucket,
        s"reports/daily_anomaly_counts/date=$date/report.pb",
        ProtobufConverter.toAnomalyPB(anomalyReport.toList))

      println("=== Pipeline4 Completed Successfully ===")
      println(s"Generated ${speedReport.size} speed reports")
      println(s"Generated ${fuelReport.size} fuel reports")
      println(s"Generated ${anomalyReport.size} anomaly reports")

    } finally {
      println("Pipeline4 completed — keeping Spark UI alive for debugging...")
      Thread.sleep(Long.MaxValue)   // <-- keeps Spark UI alive forever
      spark.stop()
    }
  }

  //----------------------------------------
  // Write protobuf to S3
  //----------------------------------------
  private def writePB(s3: S3Client, bucket: String, key: String, bytes: Array[Byte]): Unit = {
    val req = PutObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .contentType("application/x-protobuf")
      .build()
    s3.putObject(req, RequestBody.fromBytes(bytes))
    println(s"  ✓ Wrote Protobuf: $key (${bytes.length} bytes)")
  }

  //----------------------------------------
  // REPORT GENERATION
  //----------------------------------------
  private def generateSpeedReport(
                                   spark: SparkSession,
                                   telemetry: DataFrame,
                                   fleetDF: DataFrame,
                                   vehicleDF: DataFrame,
                                   date: String
                                 ): Seq[DailyAvgSpeedByFleet] = {

    import spark.implicits._

    try {
      val telemetryAliased = telemetry.alias("t")
      val vehicleAliased = vehicleDF.alias("v")
      val fleetAliased = fleetDF.alias("f")

      val joined = telemetryAliased
        .join(vehicleAliased, col("t.vehicle_id") === col("v.vehicle_id"))
        .join(fleetAliased, col("t.fleet_id") === col("f.fleet_id"))

      val grouped = joined
        .groupBy(col("f.fleet_id"), col("f.fleet_name"))
        .agg(
          avg("t.speed_kmh").as("avg_speed"),
          max("t.speed_kmh").as("max_speed"),
          min("t.speed_kmh").as("min_speed"),
          countDistinct("t.vehicle_id").as("vehicle_count"),
          count("*").as("total_readings")
        ).collect()

      grouped.map { row =>
        DailyAvgSpeedByFleet(
          fleet_id = row.getAs[Int]("fleet_id"),
          fleet_name = Option(row.getAs[String]("fleet_name")).getOrElse(s"Fleet ${row.getAs[Int]("fleet_id")}"),
          date = date,
          avg_speed = math.round(row.getAs[Double]("avg_speed") * 100.0) / 100.0,
          max_speed = math.round(row.getAs[Double]("max_speed") * 100.0) / 100.0,
          min_speed = math.round(row.getAs[Double]("min_speed") * 100.0) / 100.0,
          vehicle_count = row.getAs[Long]("vehicle_count").toInt,
          total_readings = row.getAs[Long]("total_readings")
        )
      }
    } catch {
      case e: Exception =>
        println(s"Error generating speed report: ${e.getMessage}")
        Seq.empty[DailyAvgSpeedByFleet]
    }
  }

  private def generateFuelReport(
                                  spark: SparkSession,
                                  telemetry: DataFrame,
                                  fleetDF: DataFrame,
                                  vehicleDF: DataFrame,
                                  date: String
                                ): Seq[DailyFuelConsumptionByFleet] = {

    import spark.implicits._

    try {
      val telemetryAliased = telemetry.alias("t")
      val vehicleAliased = vehicleDF.alias("v")
      val fleetAliased = fleetDF.alias("f")

      val enriched = telemetryAliased
        .join(vehicleAliased, col("t.vehicle_id") === col("v.vehicle_id"))
        .join(fleetAliased, col("t.fleet_id") === col("f.fleet_id"))
        .withColumn("distance_km", col("t.speed_kmh") * 15.0 / 3600.0)
        .withColumn("fuel_consumed", col("distance_km") * 0.08)

      val grouped = enriched
        .groupBy(col("f.fleet_id"), col("f.fleet_name"))
        .agg(
          sum("fuel_consumed").as("total_fuel_consumed"),
          sum("distance_km").as("total_distance"),
          countDistinct("t.vehicle_id").as("vehicle_count"),
          avg("t.fuel_level_percent").as("avg_fuel_level")
        ).collect()

      grouped.map { row =>
        val totalFuel = row.getAs[Double]("total_fuel_consumed")
        val totalDist = row.getAs[Double]("total_distance")

        DailyFuelConsumptionByFleet(
          fleet_id = row.getAs[Int]("fleet_id"),
          fleet_name = Option(row.getAs[String]("fleet_name")).getOrElse(s"Fleet ${row.getAs[Int]("fleet_id")}"),
          date = date,
          total_fuel_consumed = math.round(totalFuel * 100.0) / 100.0,
          avg_fuel_efficiency = if (totalFuel > 0) math.round((totalDist / totalFuel) * 100.0) / 100.0 else 0.0,
          total_distance = math.round(totalDist * 100.0) / 100.0,
          vehicle_count = row.getAs[Long]("vehicle_count").toInt,
          avg_fuel_level = math.round(row.getAs[Double]("avg_fuel_level") * 100.0) / 100.0
        )
      }
    } catch {
      case e: Exception =>
        println(s"Error generating fuel report: ${e.getMessage}")
        Seq.empty[DailyFuelConsumptionByFleet]
    }
  }

  private def generateAnomalyReport(
                                     spark: SparkSession,
                                     telemetry: DataFrame,
                                     fleetDF: DataFrame,
                                     vehicleDF: DataFrame,
                                     date: String
                                   ): Seq[DailyAnomalyCounts] = {

    import spark.implicits._

    try {
      val telemetryAliased = telemetry.alias("t")
      val vehicleAliased = vehicleDF.alias("v")
      val fleetAliased = fleetDF.alias("f")

      val enriched = telemetryAliased
        .join(vehicleAliased, col("t.vehicle_id") === col("v.vehicle_id"))
        .join(fleetAliased, col("t.fleet_id") === col("f.fleet_id"))
        .withColumn("speeding", when(col("t.speed_kmh") > 120, 1).otherwise(0))
        .withColumn("engine_overheat", when(col("t.engine_temp_c") > 105, 1).otherwise(0))
        .withColumn("sudden_stop", when(col("t.speed_kmh") < 5, 1).otherwise(0))
        .withColumn("low_fuel", when(col("t.fuel_level_percent") < 15, 1).otherwise(0))
        .withColumn("device_error", when(col("t.device_status") === "ERROR", 1).otherwise(0))

      val grouped = enriched
        .groupBy(col("f.fleet_id"), col("f.fleet_name"))
        .agg(
          count("*").as("total_records"),
          sum(when(col("t.is_anomaly") === true, 1).otherwise(0)).as("total_anomalies"),
          sum("speeding").as("speeding_count"),
          sum("engine_overheat").as("engine_overheat_count"),
          sum("sudden_stop").as("sudden_stop_count"),
          sum("low_fuel").as("low_fuel_count"),
          sum("device_error").as("device_error_count")
        ).collect()

      grouped.map { row =>
        val totalRecords = row.getAs[Long]("total_records")
        val totalAnomalies = row.getAs[Long]("total_anomalies")

        DailyAnomalyCounts(
          fleet_id = row.getAs[Int]("fleet_id"),
          fleet_name = Option(row.getAs[String]("fleet_name")).getOrElse(s"Fleet ${row.getAs[Int]("fleet_id")}"),
          date = date,
          total_anomalies = totalAnomalies.toInt,
          speeding_count = row.getAs[Long]("speeding_count").toInt,
          engine_overheat_count = row.getAs[Long]("engine_overheat_count").toInt,
          sudden_stop_count = row.getAs[Long]("sudden_stop_count").toInt,
          low_fuel_count = row.getAs[Long]("low_fuel_count").toInt,
          device_error_count = row.getAs[Long]("device_error_count").toInt,
          anomaly_rate = if (totalRecords > 0) math.round((totalAnomalies.toDouble / totalRecords) * 10000.0) / 100.0 else 0.0,
          total_readings = totalRecords
        )
      }
    } catch {
      case e: Exception =>
        println(s"Error generating anomaly report: ${e.getMessage}")
        Seq.empty[DailyAnomalyCounts]
    }
  }
}
