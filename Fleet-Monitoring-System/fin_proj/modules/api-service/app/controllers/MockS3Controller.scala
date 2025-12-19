package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDate
import java.time.format.DateTimeFormatter

case class MockHourlySummary(
  hour: Int,
  fleet_id: Int,
  total_events: Int,
  anomaly_count: Int,
  avg_speed: Double,
  avg_fuel: Double,
  unique_vehicles: Int
)

case class MockFleetSummary(
  date: String,
  fleet_id: Int,
  hourly_summaries: List[MockHourlySummary],
  daily_totals: MockHourlySummary
)

@Singleton
class MockS3Controller @Inject()(cc: ControllerComponents)(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  // JSON formatters
  implicit val mockHourlySummaryWrites: Writes[MockHourlySummary] = Json.writes[MockHourlySummary]
  implicit val mockFleetSummaryWrites: Writes[MockFleetSummary] = Json.writes[MockFleetSummary]

  /**
   * Mock hourly summaries for a specific fleet
   */
  def getMockFleetHourlySummaries(fleetId: Int, date: String): Action[AnyContent] = Action {
    try {
      // Validate date
      LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))

      // Generate mock hourly data
      val hourlySummaries = (0 to 23).map { hour =>
        val baseEvents = 100 + (hour * 10) + (fleetId * 5)
        val anomalies = (baseEvents * 0.05).toInt
        val speed = 45.0 + (hour * 2.5) + scala.util.Random.nextDouble() * 10
        val fuel = 75.0 - (hour * 1.2) + scala.util.Random.nextDouble() * 15
        val vehicles = 8 + (fleetId % 3)

        MockHourlySummary(
          hour = hour,
          fleet_id = fleetId,
          total_events = baseEvents,
          anomaly_count = anomalies,
          avg_speed = math.round(speed * 100.0) / 100.0,
          avg_fuel = math.round(fuel * 100.0) / 100.0,
          unique_vehicles = vehicles
        )
      }.toList

      // Calculate daily totals
      val totalEvents = hourlySummaries.map(_.total_events).sum
      val totalAnomalies = hourlySummaries.map(_.anomaly_count).sum
      val avgSpeed = hourlySummaries.map(h => h.avg_speed * h.total_events).sum / totalEvents
      val avgFuel = hourlySummaries.map(h => h.avg_fuel * h.total_events).sum / totalEvents
      val maxVehicles = hourlySummaries.map(_.unique_vehicles).max

      val dailyTotals = MockHourlySummary(
        hour = -1,
        fleet_id = fleetId,
        total_events = totalEvents,
        anomaly_count = totalAnomalies,
        avg_speed = math.round(avgSpeed * 100.0) / 100.0,
        avg_fuel = math.round(avgFuel * 100.0) / 100.0,
        unique_vehicles = maxVehicles
      )

      val response = MockFleetSummary(date, fleetId, hourlySummaries, dailyTotals)
      Ok(Json.toJson(response))

    } catch {
      case _: java.time.format.DateTimeParseException =>
        BadRequest(Json.obj("error" -> "Invalid date format. Use YYYY-MM-DD"))
      case e: Exception =>
        InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }

  /**
   * Mock summaries for all fleets
   */
  def getMockAllFleetsSummaries(date: String): Action[AnyContent] = Action {
    try {
      // Validate date
      LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))

      // Generate mock data for fleets 1-50
      val fleetSummaries = (1 to 50).map { fleetId =>
        val totalEvents = 2000 + (fleetId * 50) + scala.util.Random.nextInt(500)
        val anomalies = (totalEvents * 0.08).toInt
        val speed = 50.0 + scala.util.Random.nextDouble() * 20
        val fuel = 70.0 + scala.util.Random.nextDouble() * 25
        val vehicles = 8 + (fleetId % 5)

        val dailyTotals = MockHourlySummary(
          hour = -1,
          fleet_id = fleetId,
          total_events = totalEvents,
          anomaly_count = anomalies,
          avg_speed = math.round(speed * 100.0) / 100.0,
          avg_fuel = math.round(fuel * 100.0) / 100.0,
          unique_vehicles = vehicles
        )

        MockFleetSummary(date, fleetId, List.empty, dailyTotals)
      }.toList

      Ok(Json.obj(
        "date" -> date,
        "total_fleets" -> fleetSummaries.size,
        "note" -> "Mock data for testing - not real S3 data",
        "fleets" -> Json.toJson(fleetSummaries)
      ))

    } catch {
      case _: java.time.format.DateTimeParseException =>
        BadRequest(Json.obj("error" -> "Invalid date format. Use YYYY-MM-DD"))
      case e: Exception =>
        InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }
}