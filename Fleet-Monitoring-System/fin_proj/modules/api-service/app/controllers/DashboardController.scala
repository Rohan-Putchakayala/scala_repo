package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import com.typesafe.config.ConfigFactory
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDate
import java.time.format.DateTimeFormatter

case class FleetDashboardSummary(
  fleet_id: Int,
  fleet_name: String,
  date: String,
  speed_metrics: SpeedMetrics,
  fuel_metrics: FuelMetrics,
  anomaly_metrics: AnomalyMetrics
)

case class SpeedMetrics(
  avg_speed: Double,
  max_speed: Double,
  min_speed: Double,
  vehicle_count: Int,
  total_readings: Long
)

case class FuelMetrics(
  total_fuel_consumed: Double,
  avg_fuel_efficiency: Double,
  total_distance: Double,
  vehicle_count: Int,
  avg_fuel_level: Double
)

case class AnomalyMetrics(
  total_anomalies: Int,
  speeding_count: Int,
  engine_overheat_count: Int,
  sudden_stop_count: Int,
  low_fuel_count: Int,
  device_error_count: Int,
  anomaly_rate: Double,
  total_readings: Long
)

@Singleton
class DashboardController @Inject()(cc: ControllerComponents)(implicit ec: ExecutionContext)
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

  private val bucket = config.getString("s3.bucket-name")

  // JSON formatters
  implicit val speedMetricsWrites: Writes[SpeedMetrics] = Json.writes[SpeedMetrics]
  implicit val fuelMetricsWrites: Writes[FuelMetrics] = Json.writes[FuelMetrics]
  implicit val anomalyMetricsWrites: Writes[AnomalyMetrics] = Json.writes[AnomalyMetrics]
  implicit val dashboardSummaryWrites: Writes[FleetDashboardSummary] = Json.writes[FleetDashboardSummary]

  /**
   * Get daily dashboard summary for a fleet from Pipeline4 protobuf reports
   * GET /fleet/{id}/dailySummary?date=YYYY-MM-DD
   */
  def getFleetDailySummary(fleetId: Int, date: Option[String]): Action[AnyContent] = Action.async {
    Future {
      try {
        // Use provided date or default to yesterday
        val targetDate = date.getOrElse(LocalDate.now().minusDays(1).toString)
        
        // Validate date format
        LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        println(s"[Dashboard] Reading Pipeline4 reports for fleet $fleetId, date $targetDate")

        // Read the three protobuf reports from S3
        val speedReport = readSpeedReport(fleetId, targetDate)
        val fuelReport = readFuelReport(fleetId, targetDate)
        val anomalyReport = readAnomalyReport(fleetId, targetDate)

        (speedReport, fuelReport, anomalyReport) match {
          case (Some(speed), Some(fuel), Some(anomaly)) =>
            val summary = FleetDashboardSummary(
              fleet_id = fleetId,
              fleet_name = speed.fleet_name,
              date = targetDate,
              speed_metrics = SpeedMetrics(
                avg_speed = speed.avg_speed,
                max_speed = speed.max_speed,
                min_speed = speed.min_speed,
                vehicle_count = speed.vehicle_count,
                total_readings = speed.total_readings
              ),
              fuel_metrics = FuelMetrics(
                total_fuel_consumed = fuel.total_fuel_consumed,
                avg_fuel_efficiency = fuel.avg_fuel_efficiency,
                total_distance = fuel.total_distance,
                vehicle_count = fuel.vehicle_count,
                avg_fuel_level = fuel.avg_fuel_level
              ),
              anomaly_metrics = AnomalyMetrics(
                total_anomalies = anomaly.total_anomalies,
                speeding_count = anomaly.speeding_count,
                engine_overheat_count = anomaly.engine_overheat_count,
                sudden_stop_count = anomaly.sudden_stop_count,
                low_fuel_count = anomaly.low_fuel_count,
                device_error_count = anomaly.device_error_count,
                anomaly_rate = anomaly.anomaly_rate,
                total_readings = anomaly.total_readings
              )
            )
            Ok(Json.toJson(summary))

          case _ =>
            NotFound(Json.obj(
              "error" -> s"Dashboard reports not found for fleet $fleetId on date $targetDate",
              "suggestion" -> "Run Pipeline4 first to generate dashboard reports"
            ))
        }

      } catch {
        case _: java.time.format.DateTimeParseException =>
          BadRequest(Json.obj("error" -> "Invalid date format. Use YYYY-MM-DD"))
        case e: Exception =>
          println(s"[Dashboard] Error: ${e.getMessage}")
          e.printStackTrace()
          InternalServerError(Json.obj("error" -> e.getMessage))
      }
    }
  }

  private def readSpeedReport(fleetId: Int, date: String): Option[SpeedReportItem] = {
    try {
      val key = s"reports/daily_avg_speed_by_fleet/date=$date/report.pb"
      val bytes = readS3Object(key)
      parseSpeedReport(bytes, fleetId)
    } catch {
      case e: Exception =>
        println(s"[Dashboard] Error reading speed report: ${e.getMessage}")
        None
    }
  }

  private def readFuelReport(fleetId: Int, date: String): Option[FuelReportItem] = {
    try {
      val key = s"reports/daily_fuel_consumption_by_fleet/date=$date/report.pb"
      val bytes = readS3Object(key)
      parseFuelReport(bytes, fleetId)
    } catch {
      case e: Exception =>
        println(s"[Dashboard] Error reading fuel report: ${e.getMessage}")
        None
    }
  }

  private def readAnomalyReport(fleetId: Int, date: String): Option[AnomalyReportItem] = {
    try {
      val key = s"reports/daily_anomaly_counts/date=$date/report.pb"
      val bytes = readS3Object(key)
      parseAnomalyReport(bytes, fleetId)
    } catch {
      case e: Exception =>
        println(s"[Dashboard] Error reading anomaly report: ${e.getMessage}")
        None
    }
  }

  private def readS3Object(key: String): Array[Byte] = {
    val request = GetObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .build()
    
    val inputStream = s3Client.getObject(request)
    val bytes = inputStream.readAllBytes()
    inputStream.close()
    bytes
  }

  // Simple protobuf parsers for Pipeline4 reports
  private def parseSpeedReport(bytes: Array[Byte], targetFleetId: Int): Option[SpeedReportItem] = {
    try {
      // Parse the protobuf bytes to extract speed report for specific fleet
      val items = parseSpeedReportList(bytes)
      items.find(_.fleet_id == targetFleetId)
    } catch {
      case e: Exception =>
        println(s"[Dashboard] Error parsing speed report: ${e.getMessage}")
        None
    }
  }

  private def parseFuelReport(bytes: Array[Byte], targetFleetId: Int): Option[FuelReportItem] = {
    try {
      val items = parseFuelReportList(bytes)
      items.find(_.fleet_id == targetFleetId)
    } catch {
      case e: Exception =>
        println(s"[Dashboard] Error parsing fuel report: ${e.getMessage}")
        None
    }
  }

  private def parseAnomalyReport(bytes: Array[Byte], targetFleetId: Int): Option[AnomalyReportItem] = {
    try {
      val items = parseAnomalyReportList(bytes)
      items.find(_.fleet_id == targetFleetId)
    } catch {
      case e: Exception =>
        println(s"[Dashboard] Error parsing anomaly report: ${e.getMessage}")
        None
    }
  }

  // Simplified protobuf parsing (matching Pipeline4 format)
  private def parseSpeedReportList(bytes: Array[Byte]): List[SpeedReportItem] = {
    import java.nio.ByteBuffer
    val buf = ByteBuffer.wrap(bytes)
    val items = scala.collection.mutable.ListBuffer[SpeedReportItem]()
    
    try {
      // Read item count (field 1)
      if (buf.hasRemaining()) {
        val tag1 = readVarint(buf).toInt
        if ((tag1 >>> 3) == 1) {
          val itemCount = readVarint(buf).toInt
          
          // Read each item (field 2)
          for (_ <- 0 until itemCount) {
            if (buf.hasRemaining()) {
              val tag2 = readVarint(buf).toInt
              if ((tag2 >>> 3) == 2) {
                val itemBytes = readBytes(buf)
                parseSpeedItem(itemBytes).foreach(items += _)
              }
            }
          }
        }
      }
    } catch {
      case e: Exception =>
        println(s"[Dashboard] Error parsing speed report list: ${e.getMessage}")
    }
    
    items.toList
  }

  private def parseFuelReportList(bytes: Array[Byte]): List[FuelReportItem] = {
    import java.nio.ByteBuffer
    val buf = ByteBuffer.wrap(bytes)
    val items = scala.collection.mutable.ListBuffer[FuelReportItem]()
    
    try {
      if (buf.hasRemaining()) {
        val tag1 = readVarint(buf).toInt
        if ((tag1 >>> 3) == 1) {
          val itemCount = readVarint(buf).toInt
          
          for (_ <- 0 until itemCount) {
            if (buf.hasRemaining()) {
              val tag2 = readVarint(buf).toInt
              if ((tag2 >>> 3) == 2) {
                val itemBytes = readBytes(buf)
                parseFuelItem(itemBytes).foreach(items += _)
              }
            }
          }
        }
      }
    } catch {
      case e: Exception =>
        println(s"[Dashboard] Error parsing fuel report list: ${e.getMessage}")
    }
    
    items.toList
  }

  private def parseAnomalyReportList(bytes: Array[Byte]): List[AnomalyReportItem] = {
    import java.nio.ByteBuffer
    val buf = ByteBuffer.wrap(bytes)
    val items = scala.collection.mutable.ListBuffer[AnomalyReportItem]()
    
    try {
      if (buf.hasRemaining()) {
        val tag1 = readVarint(buf).toInt
        if ((tag1 >>> 3) == 1) {
          val itemCount = readVarint(buf).toInt
          
          for (_ <- 0 until itemCount) {
            if (buf.hasRemaining()) {
              val tag2 = readVarint(buf).toInt
              if ((tag2 >>> 3) == 2) {
                val itemBytes = readBytes(buf)
                parseAnomalyItem(itemBytes).foreach(items += _)
              }
            }
          }
        }
      }
    } catch {
      case e: Exception =>
        println(s"[Dashboard] Error parsing anomaly report list: ${e.getMessage}")
    }
    
    items.toList
  }

  private def parseSpeedItem(bytes: Array[Byte]): Option[SpeedReportItem] = {
    import java.nio.ByteBuffer
    val buf = ByteBuffer.wrap(bytes)
    
    var fleetId = 0
    var fleetName = ""
    var date = ""
    var avgSpeed = 0.0
    var maxSpeed = 0.0
    var minSpeed = 0.0
    var vehicleCount = 0
    var totalReadings = 0L
    
    try {
      while (buf.hasRemaining()) {
        val tag = readVarint(buf).toInt
        val fieldNum = tag >>> 3
        val wireType = tag & 7
        
        fieldNum match {
          case 1 => fleetId = readVarint(buf).toInt
          case 2 => fleetName = readString(buf)
          case 3 => date = readString(buf)
          case 4 => avgSpeed = buf.getDouble()
          case 5 => maxSpeed = buf.getDouble()
          case 6 => minSpeed = buf.getDouble()
          case 7 => vehicleCount = readVarint(buf).toInt
          case 8 => totalReadings = readVarint(buf)
          case _ => skipField(buf, wireType)
        }
      }
      
      Some(SpeedReportItem(fleetId, fleetName, date, avgSpeed, maxSpeed, minSpeed, vehicleCount, totalReadings))
    } catch {
      case e: Exception =>
        println(s"[Dashboard] Error parsing speed item: ${e.getMessage}")
        None
    }
  }

  private def parseFuelItem(bytes: Array[Byte]): Option[FuelReportItem] = {
    import java.nio.ByteBuffer
    val buf = ByteBuffer.wrap(bytes)
    
    var fleetId = 0
    var fleetName = ""
    var date = ""
    var totalFuelConsumed = 0.0
    var avgFuelEfficiency = 0.0
    var totalDistance = 0.0
    var vehicleCount = 0
    var avgFuelLevel = 0.0
    
    try {
      while (buf.hasRemaining()) {
        val tag = readVarint(buf).toInt
        val fieldNum = tag >>> 3
        val wireType = tag & 7
        
        fieldNum match {
          case 1 => fleetId = readVarint(buf).toInt
          case 2 => fleetName = readString(buf)
          case 3 => date = readString(buf)
          case 4 => totalFuelConsumed = buf.getDouble()
          case 5 => avgFuelEfficiency = buf.getDouble()
          case 6 => totalDistance = buf.getDouble()
          case 7 => vehicleCount = readVarint(buf).toInt
          case 8 => avgFuelLevel = buf.getDouble()
          case _ => skipField(buf, wireType)
        }
      }
      
      Some(FuelReportItem(fleetId, fleetName, date, totalFuelConsumed, avgFuelEfficiency, totalDistance, vehicleCount, avgFuelLevel))
    } catch {
      case e: Exception =>
        println(s"[Dashboard] Error parsing fuel item: ${e.getMessage}")
        None
    }
  }

  private def parseAnomalyItem(bytes: Array[Byte]): Option[AnomalyReportItem] = {
    import java.nio.ByteBuffer
    val buf = ByteBuffer.wrap(bytes)
    
    var fleetId = 0
    var fleetName = ""
    var date = ""
    var totalAnomalies = 0
    var speedingCount = 0
    var engineOverheatCount = 0
    var suddenStopCount = 0
    var lowFuelCount = 0
    var deviceErrorCount = 0
    var anomalyRate = 0.0
    var totalReadings = 0L
    
    try {
      while (buf.hasRemaining()) {
        val tag = readVarint(buf).toInt
        val fieldNum = tag >>> 3
        val wireType = tag & 7
        
        fieldNum match {
          case 1 => fleetId = readVarint(buf).toInt
          case 2 => fleetName = readString(buf)
          case 3 => date = readString(buf)
          case 4 => totalAnomalies = readVarint(buf).toInt
          case 5 => speedingCount = readVarint(buf).toInt
          case 6 => engineOverheatCount = readVarint(buf).toInt
          case 7 => suddenStopCount = readVarint(buf).toInt
          case 8 => lowFuelCount = readVarint(buf).toInt
          case 9 => deviceErrorCount = readVarint(buf).toInt
          case 10 => anomalyRate = buf.getDouble()
          case 11 => totalReadings = readVarint(buf)
          case _ => skipField(buf, wireType)
        }
      }
      
      Some(AnomalyReportItem(fleetId, fleetName, date, totalAnomalies, speedingCount, engineOverheatCount, 
        suddenStopCount, lowFuelCount, deviceErrorCount, anomalyRate, totalReadings))
    } catch {
      case e: Exception =>
        println(s"[Dashboard] Error parsing anomaly item: ${e.getMessage}")
        None
    }
  }

  // Protobuf parsing helpers
  private def readVarint(buf: java.nio.ByteBuffer): Long = {
    var shift = 0
    var result = 0L
    while (true) {
      if (!buf.hasRemaining()) throw new IllegalArgumentException("Unexpected end of buffer")
      val b = buf.get()
      result |= ((b & 0x7F).toLong << shift)
      if ((b & 0x80) == 0) return result
      shift += 7
      if (shift > 63) throw new IllegalArgumentException("Varint too long")
    }
    0L
  }

  private def readString(buf: java.nio.ByteBuffer): String = {
    val len = readVarint(buf).toInt
    val arr = new Array[Byte](len)
    buf.get(arr)
    new String(arr, "UTF-8")
  }

  private def readBytes(buf: java.nio.ByteBuffer): Array[Byte] = {
    val len = readVarint(buf).toInt
    val arr = new Array[Byte](len)
    buf.get(arr)
    arr
  }

  private def skipField(buf: java.nio.ByteBuffer, wireType: Int): Unit = wireType match {
    case 0 => readVarint(buf)
    case 1 => buf.position(buf.position() + 8)
    case 2 =>
      val len = readVarint(buf).toInt
      buf.position(buf.position() + len)
    case 5 => buf.position(buf.position() + 4)
    case _ => buf.position(buf.limit())
  }
}

// Data classes for parsing protobuf reports
case class SpeedReportItem(
  fleet_id: Int,
  fleet_name: String,
  date: String,
  avg_speed: Double,
  max_speed: Double,
  min_speed: Double,
  vehicle_count: Int,
  total_readings: Long
)

case class FuelReportItem(
  fleet_id: Int,
  fleet_name: String,
  date: String,
  total_fuel_consumed: Double,
  avg_fuel_efficiency: Double,
  total_distance: Double,
  vehicle_count: Int,
  avg_fuel_level: Double
)

case class AnomalyReportItem(
  fleet_id: Int,
  fleet_name: String,
  date: String,
  total_anomalies: Int,
  speeding_count: Int,
  engine_overheat_count: Int,
  sudden_stop_count: Int,
  low_fuel_count: Int,
  device_error_count: Int,
  anomaly_rate: Double,
  total_readings: Long
)