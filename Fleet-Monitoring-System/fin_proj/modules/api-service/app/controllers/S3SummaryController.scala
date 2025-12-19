package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import com.typesafe.config.ConfigFactory
import com.smartfleet.common.utils.ProtobufConverter
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{ListObjectsV2Request, GetObjectRequest}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}
import java.time.{Instant, ZoneId, LocalDate}
import java.time.format.DateTimeFormatter

case class HourlySummary(
                          hour: Int,
                          fleet_id: Int,
                          total_events: Int,
                          anomaly_count: Int,
                          avg_speed: Double,
                          avg_fuel: Double,
                          avg_engine_temp: Double,
                          unique_vehicles: Int,
                          distance_km: Double
                        )

case class DailySummaryFromS3(
                               date: String,
                               fleet_id: Int,
                               hourly_summaries: List[HourlySummary],
                               daily_totals: HourlySummary
                             )

@Singleton
class S3SummaryController @Inject()(cc: ControllerComponents)(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  private val config = ConfigFactory.load()

  // S3 Client
  private val s3Client = {
    val region = Region.of(config.getString("s3.region"))
    val credentials = AwsBasicCredentials.create(
      config.getString("aws.access-key"),
      config.getString("aws.secret-key")
    )
    S3Client.builder()
      .region(region)
      .credentialsProvider(StaticCredentialsProvider.create(credentials))
      .build()
  }

  private val bucket     = config.getString("s3.bucket-name")
  private val rawPrefix  = config.getString("s3.paths.raw")

  // JSON formatters
  implicit val hourlySummaryWrites: Writes[HourlySummary] = Json.writes[HourlySummary]
  implicit val dailySummaryWrites: Writes[DailySummaryFromS3] = Json.writes[DailySummaryFromS3]

  /**
   * FIXED:
   * Reads ALL protobuf files from S3 for the given date
   */
  def getFleetHourlySummaries(fleetId: Int, date: String): Action[AnyContent] = Action.async {
    Future {
      val startTime = System.currentTimeMillis()
      try {
        // Validate date
        LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val datePrefix = s"$rawPrefix/date=$date/"
        println(s"[S3Summary] Reading S3 prefix: $datePrefix")

        // List all files
        val listReq = ListObjectsV2Request.builder()
          .bucket(bucket)
          .prefix(datePrefix)
          .build()

        val s3Objects = s3Client.listObjectsV2(listReq).contents().asScala.toList
        println(s"[S3Summary] Found ${s3Objects.size} protobuf files for date=$date")

        if (s3Objects.isEmpty) {
           NotFound(Json.obj(
            "error" -> s"No S3 data for date: $date"
          ))
        } else {



        // Read protobuf files with streaming approach
        println(s"[S3Summary] Processing ${s3Objects.size} files for fleet $fleetId")
        
        val allEvents = s3Objects.zipWithIndex.flatMap { case (obj, index) =>
          try {
            if (index % 5 == 0) {
              println(s"[S3Summary] Processing file ${index + 1}/${s3Objects.size}: ${obj.key()}")
            }
            
            val req = GetObjectRequest.builder()
              .bucket(bucket)
              .key(obj.key())
              .build()

            val data = s3Client.getObject(req).readAllBytes()
            val events = ProtobufConverter.fromBatchProtobufBytes(data)
            
            // Filter for target fleet immediately to reduce memory usage
            events.filter(_.fleet_id == fleetId)
          } catch {
            case e: Exception =>
              println(s"[S3Summary] Error reading ${obj.key()}: ${e.getMessage}")
              List.empty
          }
        }

        val fleetEvents = allEvents // Already filtered above
        println(s"[S3Summary] Found ${fleetEvents.size} events for fleet $fleetId")

        // Group events by hour
        val eventsByHour =
          fleetEvents.groupBy { ev =>
            Instant.ofEpochMilli(ev.timestamp).atZone(ZoneId.of("UTC")).getHour
          }

        val hourlySummaries = (0 to 23).map { hour =>
          val events = eventsByHour.getOrElse(hour, Nil)

          if (events.isEmpty) {
            HourlySummary(hour, fleetId, 0, 0, 0, 0, 0, 0, 0)
          } else {
            val total = events.size
            val anomalies = events.count(_.is_anomaly)
            val avgSpeed = events.map(_.speed_kmh).sum / total
            val avgFuel = events.map(_.fuel_level_percent).sum / total
            val avgTemp = events.map(_.engine_temp_c).sum / total
            val uniqueVehicles = events.map(_.vehicle_id).distinct.size
            val distanceKm = events.map(e => e.speed_kmh * (15.0 / 3600.0)).sum

            HourlySummary(
              hour = hour,
              fleet_id = fleetId,
              total_events = total,
              anomaly_count = anomalies,
              avg_speed = BigDecimal(avgSpeed).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
              avg_fuel = BigDecimal(avgFuel).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
              avg_engine_temp = BigDecimal(avgTemp).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
              unique_vehicles = uniqueVehicles,
              distance_km = BigDecimal(distanceKm).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
            )
          }
        }.toList

        // Daily totals
        val totalEvents = hourlySummaries.map(_.total_events).sum
        val totalAnomalies = hourlySummaries.map(_.anomaly_count).sum
        val totalDistance = hourlySummaries.map(_.distance_km).sum
        val maxVehicles = hourlySummaries.map(_.unique_vehicles).maxOption.getOrElse(0)

        val avgSpeed =
          if (totalEvents > 0)
            hourlySummaries.map(h => h.avg_speed * h.total_events).sum / totalEvents
          else 0

        val avgFuel =
          if (totalEvents > 0)
            hourlySummaries.map(h => h.avg_fuel * h.total_events).sum / totalEvents
          else 0

        val avgTemp =
          if (totalEvents > 0)
            hourlySummaries.map(h => h.avg_engine_temp * h.total_events).sum / totalEvents
          else 0

        val dailyTotals = HourlySummary(
          hour = -1,
          fleet_id = fleetId,
          total_events = totalEvents,
          anomaly_count = totalAnomalies,
          avg_speed = BigDecimal(avgSpeed).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
          avg_fuel = BigDecimal(avgFuel).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
          avg_engine_temp = BigDecimal(avgTemp).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
          unique_vehicles = maxVehicles,
          distance_km = BigDecimal(totalDistance).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
        )

        val endTime = System.currentTimeMillis()
        val processingTime = (endTime - startTime) / 1000.0
        
        val response = DailySummaryFromS3(date, fleetId, hourlySummaries, dailyTotals)
        Ok(Json.obj(
          "fleet_summary" -> Json.toJson(response),
          "processing_time_seconds" -> processingTime,
          "total_events_processed" -> fleetEvents.size,
          "files_processed" -> s3Objects.size
        ))
        }

      } catch {
        case _: java.time.format.DateTimeParseException =>
          BadRequest(Json.obj("error" -> "Invalid date format. Use YYYY-MM-DD"))
        case e: Exception =>
          e.printStackTrace()
          InternalServerError(Json.obj("error" -> e.getMessage))
      }
    }
  }

  /**
   * Get hourly summaries for ALL fleets for a given date
   */
  def getAllFleetsHourlySummaries(date: String): Action[AnyContent] = Action.async {
    Future {
      val startTime = System.currentTimeMillis()
      try {
        // Validate date
        LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val datePrefix = s"$rawPrefix/date=$date/"
        println(s"[S3Summary] Reading S3 prefix for all fleets: $datePrefix")

        // List all files
        val listReq = ListObjectsV2Request.builder()
          .bucket(bucket)
          .prefix(datePrefix)
          .build()

        val s3Objects = s3Client.listObjectsV2(listReq).contents().asScala.toList
        println(s"[S3Summary] Found ${s3Objects.size} protobuf files for date=$date")

        if (s3Objects.isEmpty) {
          NotFound(Json.obj(
            "error" -> s"No S3 data for date: $date"
          ))
        } else {

        // Process files in batches to avoid memory issues
        println(s"[S3Summary] Processing ${s3Objects.size} files for all fleets")
        
        // Process files in smaller batches
        val batchSize = 5
        val allEvents = s3Objects.grouped(batchSize).zipWithIndex.flatMap { case (batch, batchIndex) =>
          println(s"[S3Summary] Processing batch ${batchIndex + 1}/${(s3Objects.size + batchSize - 1) / batchSize}")
          
          batch.flatMap { obj =>
            try {
              val req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(obj.key())
                .build()

              val data = s3Client.getObject(req).readAllBytes()
              ProtobufConverter.fromBatchProtobufBytes(data)
            } catch {
              case e: Exception =>
                println(s"[S3Summary] Error reading ${obj.key()}: ${e.getMessage}")
                List.empty
            }
          }
        }.toList

        // Group events by fleet
        println(s"[S3Summary] Loaded ${allEvents.size} total events, grouping by fleet...")
        val eventsByFleet = allEvents.groupBy(_.fleet_id)
        println(s"[S3Summary] Found ${eventsByFleet.size} fleets with data")

        val fleetSummaries = eventsByFleet.zipWithIndex.map { case ((fleetId, fleetEvents), index) =>
          if (index % 10 == 0) {
            println(s"[S3Summary] Processing fleet ${index + 1}/${eventsByFleet.size}: Fleet $fleetId (${fleetEvents.size} events)")
          }
          // Group events by hour
          val eventsByHour = fleetEvents.groupBy { ev =>
            java.time.Instant.ofEpochMilli(ev.timestamp).atZone(java.time.ZoneId.of("UTC")).getHour
          }

          val hourlySummaries = (0 to 23).map { hour =>
            val events = eventsByHour.getOrElse(hour, Nil)

            if (events.isEmpty) {
              HourlySummary(hour, fleetId, 0, 0, 0, 0, 0, 0, 0)
            } else {
              val total = events.size
              val anomalies = events.count(_.is_anomaly)
              val avgSpeed = events.map(_.speed_kmh).sum / total
              val avgFuel = events.map(_.fuel_level_percent).sum / total
              val avgTemp = events.map(_.engine_temp_c).sum / total
              val uniqueVehicles = events.map(_.vehicle_id).distinct.size
              val distanceKm = events.map(e => e.speed_kmh * (15.0 / 3600.0)).sum

              HourlySummary(
                hour = hour,
                fleet_id = fleetId,
                total_events = total,
                anomaly_count = anomalies,
                avg_speed = BigDecimal(avgSpeed).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
                avg_fuel = BigDecimal(avgFuel).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
                avg_engine_temp = BigDecimal(avgTemp).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
                unique_vehicles = uniqueVehicles,
                distance_km = BigDecimal(distanceKm).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
              )
            }
          }.toList

          // Daily totals for this fleet
          val totalEvents = hourlySummaries.map(_.total_events).sum
          val totalAnomalies = hourlySummaries.map(_.anomaly_count).sum
          val totalDistance = hourlySummaries.map(_.distance_km).sum
          val maxVehicles = hourlySummaries.map(_.unique_vehicles).maxOption.getOrElse(0)

          val avgSpeed = if (totalEvents > 0) hourlySummaries.map(h => h.avg_speed * h.total_events).sum / totalEvents else 0
          val avgFuel = if (totalEvents > 0) hourlySummaries.map(h => h.avg_fuel * h.total_events).sum / totalEvents else 0
          val avgTemp = if (totalEvents > 0) hourlySummaries.map(h => h.avg_engine_temp * h.total_events).sum / totalEvents else 0

          val dailyTotals = HourlySummary(
            hour = -1,
            fleet_id = fleetId,
            total_events = totalEvents,
            anomaly_count = totalAnomalies,
            avg_speed = BigDecimal(avgSpeed).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
            avg_fuel = BigDecimal(avgFuel).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
            avg_engine_temp = BigDecimal(avgTemp).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
            unique_vehicles = maxVehicles,
            distance_km = BigDecimal(totalDistance).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
          )

          DailySummaryFromS3(date, fleetId, hourlySummaries, dailyTotals)
        }.toList.sortBy(_.fleet_id)

        val endTime = System.currentTimeMillis()
        val processingTime = (endTime - startTime) / 1000.0
        
        Ok(Json.obj(
          "date" -> date,
          "total_fleets" -> fleetSummaries.size,
          "processing_time_seconds" -> processingTime,
          "total_events_processed" -> allEvents.size,
          "fleets" -> Json.toJson(fleetSummaries)
        ))
        }

      } catch {
        case _: java.time.format.DateTimeParseException =>
          BadRequest(Json.obj("error" -> "Invalid date format. Use YYYY-MM-DD"))
        case e: Exception =>
          e.printStackTrace()
          InternalServerError(Json.obj("error" -> e.getMessage))
      }
    }
  }

}
