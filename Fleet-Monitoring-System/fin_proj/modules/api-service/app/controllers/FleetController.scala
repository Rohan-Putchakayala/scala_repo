package controllers

import javax.inject._
import play.api.mvc._
import com.typesafe.config.ConfigFactory
import com.smartfleet.common.database.MySQLDatabase
import play.api.libs.json._
import com.smartfleet.common.models.{Fleet, Vehicle, Driver}

@Singleton
class FleetController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  private val config = ConfigFactory.load()
  private val mysql = new MySQLDatabase(config)

  implicit val fleetWrites: Writes[Fleet] = new Writes[Fleet] {
    def writes(f: Fleet): JsValue = Json.obj(
      "fleet_id" -> f.fleet_id,
      "fleet_name" -> f.fleet_name,
      "city" -> f.city,
      "manager_name" -> f.manager_name,
      "contact_number" -> f.contact_number,
      "created_at" -> f.created_at.toEpochMilli
    )
  }

  def getFleets: Action[AnyContent] = Action {
    try {
      val fleets = mysql.getAllFleets()
      Ok(Json.toJson(fleets))
    } catch {
      case e: Exception =>
        InternalServerError(Json.obj("error" -> s"Failed to fetch fleets: ${e.getMessage}"))
    }
  }

  implicit val vehicleWrites: Writes[Vehicle] = new Writes[Vehicle] {
    def writes(v: Vehicle): JsValue = Json.obj(
      "vehicle_id" -> v.vehicle_id,
      "fleet_id" -> v.fleet_id,
      "vehicle_type" -> v.vehicle_type,
      "model" -> v.model,
      "year" -> v.year,
      "status" -> v.status
    )
  }

  def getFleet(id: Int): Action[AnyContent] = Action {
    mysql.getFleet(id) match {
      case scala.util.Success(Some(fleet)) => 
        // Also get vehicles for this fleet
        try {
          val vehicles = mysql.getAllVehicles().filter(_.fleet_id == id)
          Ok(Json.obj(
            "fleet" -> Json.toJson(fleet),
            "vehicles" -> Json.toJson(vehicles),
            "vehicle_count" -> vehicles.size
          ))
        } catch {
          case e: Exception =>
            // Fallback to just fleet info if vehicles fail
            Ok(Json.obj(
              "fleet" -> Json.toJson(fleet),
              "vehicles" -> Json.arr(),
              "vehicle_count" -> 0,
              "note" -> "Vehicle details unavailable"
            ))
        }
      case scala.util.Success(None) => NotFound(Json.obj("error" -> s"Fleet $id not found"))
      case scala.util.Failure(e) => InternalServerError(Json.obj("error" -> s"Failed to fetch fleet: ${e.getMessage}"))
    }
  }
}