package controllers

import javax.inject._
import play.api.mvc._
import com.typesafe.config.ConfigFactory
import com.smartfleet.common.database.DynamoDatabase
import play.api.libs.json._
import com.smartfleet.common.models.DynamoTelemetryRecord

@Singleton
class TelemetryController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  private val config = ConfigFactory.load()
  private val dynamo = new DynamoDatabase(config)

  implicit val recordWrites: Writes[DynamoTelemetryRecord] = new Writes[DynamoTelemetryRecord] {
    def writes(r: DynamoTelemetryRecord): JsValue = Json.obj(
      "fleet_id" -> r.fleet_id,
      "timestamp" -> r.timestamp,
      "vehicle_id" -> r.vehicle_id,
      "speed_kmh" -> r.speed_kmh,
      "fuel_level_percent" -> r.fuel_level_percent,
      "engine_temp_c" -> r.engine_temp_c,
      "gps_lat" -> r.gps_lat,
      "gps_long" -> r.gps_long,
      "device_status" -> r.device_status
    )
  }

  def recentReadings(fleetId: Int, limit: Int): Action[AnyContent] = Action {
    dynamo.getRecentReadings(fleetId, limit) match {
      case scala.util.Success(readings) => 
        Ok(Json.obj(
          "fleet_id" -> fleetId,
          "limit" -> limit,
          "count" -> readings.size,
          "readings" -> Json.toJson(readings)
        ))
      case scala.util.Failure(e) => 
        InternalServerError(Json.obj("error" -> s"Failed to fetch readings: ${e.getMessage}"))
    }
  }

  def vehicleReadings(fleetId: Int, vehicleId: String, limit: Int): Action[AnyContent] = Action {
    dynamo.getRecentReadingsForVehicle(fleetId, vehicleId, limit) match {
      case scala.util.Success(readings) => 
        Ok(Json.obj(
          "fleet_id" -> fleetId,
          "vehicle_id" -> vehicleId,
          "limit" -> limit,
          "count" -> readings.size,
          "readings" -> Json.toJson(readings)
        ))
      case scala.util.Failure(e) => 
        InternalServerError(Json.obj("error" -> s"Failed to fetch vehicle readings: ${e.getMessage}"))
    }
  }
}