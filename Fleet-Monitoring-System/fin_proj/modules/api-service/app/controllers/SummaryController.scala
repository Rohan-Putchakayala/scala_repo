package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import com.typesafe.config.ConfigFactory
import com.smartfleet.common.database.MySQLDatabase
import com.smartfleet.common.models.FleetDailySummary

@Singleton
class SummaryController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  private val config = ConfigFactory.load()
  private val mysql = new MySQLDatabase(config)

  def getFleetSummary(fleetId: Int, date: String): Action[AnyContent] = Action {
    mysql.getFleetDailySummary(fleetId, date) match {
      case scala.util.Success(Some(summary)) =>
        val json = Json.obj(
          "record_id" -> summary.record_id,
          "fleet_id" -> summary.fleet_id,
          "record_date" -> summary.record_date,
          "total_distance_km" -> summary.total_distance_km,
          "avg_speed" -> summary.avg_speed,
          "fuel_consumed_liters" -> summary.fuel_consumed_liters,
          "anomaly_count" -> summary.anomaly_count,
          "generated_at" -> summary.generated_at.toEpochMilli
        )
        Ok(json)
      case scala.util.Success(None) => 
        NotFound(Json.obj("error" -> s"No summary found for fleet $fleetId on date $date"))
      case scala.util.Failure(e) => 
        InternalServerError(Json.obj("error" -> s"Failed to fetch summary: ${e.getMessage}"))
    }
  }
}
