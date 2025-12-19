package com.smartfleet.common.models

import java.time.Instant
import java.util.UUID

/**
 * Vehicle telemetry event as received from Kafka
 */
case class VehicleTelemetryEvent(
  vehicle_id: String,
  fleet_id: Int,
  speed_kmh: Double,
  fuel_level_percent: Double,
  engine_temp_c: Double,
  gps_lat: Double,
  gps_long: Double,
  device_status: String,
  timestamp: Long
) {
  def toEnrichedEvent(anomalyDetected: Boolean = false, anomalyType: Option[String] = None): EnrichedTelemetryEvent = {
    EnrichedTelemetryEvent(
      reading_id = UUID.randomUUID().toString,
      vehicle_id = this.vehicle_id,
      fleet_id = this.fleet_id,
      speed_kmh = this.speed_kmh,
      fuel_level_percent = this.fuel_level_percent,
      engine_temp_c = this.engine_temp_c,
      gps_lat = this.gps_lat,
      gps_long = this.gps_long,
      device_status = this.device_status,
      timestamp = this.timestamp,
      ingestion_timestamp = Instant.now().toEpochMilli,
      is_anomaly = anomalyDetected,
      anomaly_type = anomalyType
    )
  }
}

/**
 * Enriched telemetry event after processing
 */
case class EnrichedTelemetryEvent(
  reading_id: String,
  vehicle_id: String,
  fleet_id: Int,
  speed_kmh: Double,
  fuel_level_percent: Double,
  engine_temp_c: Double,
  gps_lat: Double,
  gps_long: Double,
  device_status: String,
  timestamp: Long,
  ingestion_timestamp: Long,
  is_anomaly: Boolean,
  anomaly_type: Option[String] = None
)

/**
 * DynamoDB record structure for recent readings
 */
case class DynamoTelemetryRecord(
  fleet_id: Int,
  timestamp: Long,
  vehicle_id: String,
  speed_kmh: Double,
  fuel_level_percent: Double,
  engine_temp_c: Double,
  gps_lat: Double,
  gps_long: Double,
  device_status: String
)



/**
 * Vehicle alert for real-time notifications
 */
case class VehicleAlert(
  alert_id: String,
  vehicle_id: String,
  fleet_id: Int,
  alert_type: String,
  description: String,
  severity_score: Double,
  alert_timestamp: Long,
  gps_lat: Double,
  gps_long: Double,
  acknowledged: Boolean = false,
  acknowledged_at: Option[Long] = None,
  acknowledged_by: Option[String] = None
)

/**
 * Fleet operational data models
 */
case class Fleet(
  fleet_id: Int,
  fleet_name: String,
  city: String,
  manager_name: String,
  contact_number: String,
  created_at: Instant
)

case class Vehicle(
  vehicle_id: String,
  fleet_id: Int,
  vehicle_type: String,
  model: String,
  year: Int,
  status: String
)

case class Driver(
  driver_id: Int,
  vehicle_id: String,
  name: String,
  license_number: String,
  contact: String
)

case class FleetDailySummary(
  record_id: String,
  fleet_id: Int,
  total_distance_km: Double,
  avg_speed: Double,
  fuel_consumed_liters: Double,
  anomaly_count: Int,
  record_date: String, // YYYY-MM-DD
  generated_at: Instant
)

/**
 * Anomaly detection constants and utilities
 */
object AnomalyDetector {
  val SPEED_LIMIT_KMH = 100.0
  val SUDDEN_STOP_THRESHOLD = -30.0 // km/h change
  val ENGINE_TEMP_LIMIT = 105.0 // Celsius
  val LOW_FUEL_THRESHOLD = 10.0 // percent

  def detectAnomalies(event: VehicleTelemetryEvent, previousSpeed: Option[Double] = None): (Boolean, Option[String]) = {
    // Primary anomaly conditions as per requirements: speed > 100 km/h OR engine_temp > 105°C
    if (event.speed_kmh > 100.0) {
      return (true, Some("SPEEDING"))
    }

    if (event.engine_temp_c > 105.0) {
      return (true, Some("ENGINE_OVERHEAT"))
    }

    // Additional anomaly detection
    // Sudden stop detection
    previousSpeed.foreach { prevSpeed =>
      val speedChange = event.speed_kmh - prevSpeed
      if (speedChange < SUDDEN_STOP_THRESHOLD) {
        return (true, Some("SUDDEN_STOP"))
      }
    }

    // Low fuel anomaly
    if (event.fuel_level_percent < LOW_FUEL_THRESHOLD) {
      return (true, Some("LOW_FUEL"))
    }

    // Device error
    if (event.device_status == "ERROR") {
      return (true, Some("DEVICE_ERROR"))
    }

    (false, None)
  }

  def validateFields(event: VehicleTelemetryEvent): Boolean = {
    event.vehicle_id.nonEmpty &&
    event.fleet_id > 0 &&
    event.speed_kmh >= 0 && event.speed_kmh <= 200 &&
    event.fuel_level_percent >= 0 && event.fuel_level_percent <= 100 &&
    event.engine_temp_c >= -40 && event.engine_temp_c <= 150 &&
    event.gps_lat >= -90 && event.gps_lat <= 90 &&
    event.gps_long >= -180 && event.gps_long <= 180 &&
    Set("OK", "WARNING", "ERROR").contains(event.device_status) &&
    event.timestamp > 0
  }
}

/**
 * JSON serialization support
 */
object JsonFormats {
  import spray.json._
  import spray.json.DefaultJsonProtocol._

  // Custom Instant serializer
  implicit object InstantJsonFormat extends RootJsonFormat[Instant] {
    def write(instant: Instant): JsValue = JsNumber(instant.toEpochMilli)
    def read(value: JsValue): Instant = value match {
      case JsNumber(epochMilli) => Instant.ofEpochMilli(epochMilli.toLong)
      case _ => throw DeserializationException("Expected epoch milliseconds")
    }
  }

  implicit val vehicleTelemetryEventFormat = jsonFormat9(VehicleTelemetryEvent)
  implicit val enrichedTelemetryEventFormat = jsonFormat13(EnrichedTelemetryEvent)
  implicit val dynamoTelemetryRecordFormat = jsonFormat9(DynamoTelemetryRecord)
  implicit val vehicleAlertFormat = jsonFormat12(VehicleAlert)
  implicit val fleetFormat = jsonFormat6(Fleet)
  implicit val vehicleFormat = jsonFormat6(Vehicle)
  implicit val driverFormat = jsonFormat5(Driver)
  implicit val fleetDailySummaryFormat = jsonFormat8(FleetDailySummary)
}

/**
 * Pipeline4 Dashboard Report Models
 */
case class DailyAvgSpeedByFleet(
  fleet_id: Int,
  fleet_name: String,
  date: String,
  avg_speed: Double,
  max_speed: Double,
  min_speed: Double,
  vehicle_count: Int,
  total_readings: Long
)

case class DailyFuelConsumptionByFleet(
  fleet_id: Int,
  fleet_name: String,
  date: String,
  total_fuel_consumed: Double,
  avg_fuel_efficiency: Double,
  total_distance: Double,
  vehicle_count: Int,
  avg_fuel_level: Double
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
  anomaly_rate: Double,
  total_readings: Long
)