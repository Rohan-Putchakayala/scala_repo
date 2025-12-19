package com.smartfleet.batch

import com.typesafe.config.{Config, ConfigFactory}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{ListObjectsV2Request, GetObjectRequest, PutObjectRequest}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import java.time.{Instant, LocalDate, ZoneId}
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters._

object BatchPipelineApp {
  case class FleetAgg(
                       totalDistanceKm: Double,
                       avgSpeed: Double,
                       fuelConsumedLiters: Double,
                       anomalyCount: Int,
                       readingCount: Int
                     )

  def main(args: Array[String]): Unit = {
    // Load config - if ENVIRONMENT is set to "production", use that, otherwise use default
    val env = sys.env.getOrElse("ENVIRONMENT", "default")
    val config = if (env == "production") {
      ConfigFactory.load().getConfig("production")
    } else {
      ConfigFactory.load()
    }

    val region = Region.of(config.getString("s3.region"))
    val bucket = config.getString("s3.bucket-name")
    val telemetryPrefix = config.getString("s3.paths.raw")
    val summariesPrefix = config.getString("s3.paths.processed")


    // CRITICAL FIX: Build S3 client with explicit region AND credentials provider
    val s3 = S3Client.builder()
      .region(region)
      .credentialsProvider(DefaultCredentialsProvider.create())
      .build()

    println(s"[BatchPipeline] Using S3 region: ${region}")
    println(s"[BatchPipeline] Bucket: ${bucket}")

    val dateToProcess = args.headOption.getOrElse(LocalDate.now(ZoneId.of("UTC")).minusDays(1).format(DateTimeFormatter.ISO_DATE))
    val prefixForDate = s"$telemetryPrefix/date=$dateToProcess/"

    println(s"[BatchPipeline] Processing data for date: $dateToProcess")
    println(s"[BatchPipeline] Looking for files in: s3://$bucket/$prefixForDate")

    val listReq = ListObjectsV2Request.builder().bucket(bucket).prefix(prefixForDate).build()
    val listed = s3.listObjectsV2(listReq)

    println(s"[BatchPipeline] Found ${listed.contents().size()} objects to process")

    import com.smartfleet.common.utils.ProtobufConverter
    import com.smartfleet.common.database.MySQLDatabase
    import com.smartfleet.common.models.{FleetDailySummary, EnrichedTelemetryEvent}
    import java.util.UUID

    val intervalSeconds = config.getInt("simulator.telemetry-interval")
    val fuelTankLiters = 60.0

    val perFleet = scala.collection.mutable.Map[Int, FleetAgg]()
    val prevFuelByVehicle = scala.collection.mutable.Map[String, Double]()

    // Initialize MySQL database
    val mysql = new MySQLDatabase(config)
    mysql.initializeTables()

    listed.contents().asScala.foreach { obj =>
      try {
        val getReq = GetObjectRequest.builder().bucket(bucket).key(obj.key()).build()
        val bytes = s3.getObjectAsBytes(getReq).asByteArray()
        
        // Parse protobuf bytes back to events using our custom converter
        val events = ProtobufConverter.fromBatchProtobufBytes(bytes)
        
        events.foreach { event =>
          val distanceKm = event.speed_kmh * (intervalSeconds.toDouble / 3600.0)
          val prevFuel = prevFuelByVehicle.get(event.vehicle_id)
          val fuelDropPercent = prevFuel.map(_ - event.fuel_level_percent).getOrElse(0.0)
          prevFuelByVehicle.update(event.vehicle_id, event.fuel_level_percent)
          val fuelConsumedLiters = if (fuelDropPercent > 0) fuelDropPercent / 100.0 * fuelTankLiters else 0.0

          val current = perFleet.getOrElse(event.fleet_id, FleetAgg(0.0, 0.0, 0.0, 0, 0))
          val newCount = current.readingCount + 1
          val newAvgSpeed = ((current.avgSpeed * current.readingCount) + event.speed_kmh) / newCount
          val newAgg = current.copy(
            totalDistanceKm = current.totalDistanceKm + distanceKm,
            avgSpeed = newAvgSpeed,
            fuelConsumedLiters = current.fuelConsumedLiters + fuelConsumedLiters,
            anomalyCount = current.anomalyCount + (if (event.is_anomaly) 1 else 0),
            readingCount = newCount
          )
          perFleet.update(event.fleet_id, newAgg)
        }
      } catch {
        case e: Exception =>
          println(s"[BatchPipeline] Error processing file ${obj.key()}: ${e.getMessage}")
      }
    }

    println(s"[BatchPipeline] Processed ${perFleet.size} fleets")

    // Write summaries to MySQL instead of S3
    perFleet.foreach { case (fleetId, agg) =>
      val summary = FleetDailySummary(
        record_id = UUID.randomUUID().toString,
        fleet_id = fleetId,
        total_distance_km = math.round(agg.totalDistanceKm * 100.0) / 100.0,
        avg_speed = math.round(agg.avgSpeed * 100.0) / 100.0,
        fuel_consumed_liters = math.round(agg.fuelConsumedLiters * 100.0) / 100.0,
        anomaly_count = agg.anomalyCount,
        record_date = dateToProcess,
        generated_at = java.time.Instant.now()
      )

      try {
        mysql.insertFleetDailySummary(summary) match {
          case scala.util.Success(_) =>
            println(s"[BatchPipeline] ✓ Wrote summary for Fleet-$fleetId to MySQL")
          case scala.util.Failure(e) =>
            println(s"[BatchPipeline] ✗ Failed to write summary for Fleet-$fleetId: ${e.getMessage}")
        }
      } catch {
        case e: Exception =>
          println(s"[BatchPipeline] ✗ Error writing summary for Fleet-$fleetId: ${e.getMessage}")
      }
    }

    println(s"[BatchPipeline] Batch processing complete!")
  }
}
