package controllers

import models._
import models.Models._
import play.api.libs.json._
import play.api.mvc._
import services.ReservationService
import models.DateTimeSupport._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReservationController @Inject()(
  val controllerComponents: ControllerComponents,
  reservationService: ReservationService
)(implicit ec: ExecutionContext) extends BaseController {

  /**
   * POST /api/reservations
   * Create a new room reservation
   */
  def createReservation(): Action[JsValue] = Action.async(parse.json) { request =>
    request.body.validate[ReservationRequest].fold(
      errors => {
        Future.successful(BadRequest(Json.obj("error" -> "Invalid request format", "details" -> JsError.toJson(errors))))
      },
      reservationRequest => {
        reservationService.createReservation(reservationRequest).map { response =>
          Ok(Json.toJson(response))
        }.recover {
          case ex: IllegalArgumentException => BadRequest(Json.obj("error" -> ex.getMessage))
          case ex: IllegalStateException => Conflict(Json.obj("error" -> ex.getMessage))
          case ex: Exception => InternalServerError(Json.obj("error" -> s"Failed to create reservation: ${ex.getMessage}"))
        }
      }
    )
  }

  /**
   * GET /api/reservations/availability
   * Check room availability for a time range
   */
  def checkAvailability(): Action[AnyContent] = Action.async { request =>
    val roomIdOpt = request.getQueryString("roomId").map(_.toLong)
    val startTimeOpt = request.getQueryString("startTime")
    val endTimeOpt = request.getQueryString("endTime")

    (roomIdOpt, startTimeOpt, endTimeOpt) match {
      case (Some(roomId), Some(startTimeStr), Some(endTimeStr)) =>
        try {
          val startTime = java.time.LocalDateTime.parse(startTimeStr)
          val endTime = java.time.LocalDateTime.parse(endTimeStr)
          reservationService.getRoomAvailability(roomId, startTime, endTime).map { response =>
            Ok(Json.toJson(response))
          }.recover {
            case ex: IllegalArgumentException => BadRequest(Json.obj("error" -> ex.getMessage))
            case ex: Exception => InternalServerError(Json.obj("error" -> s"Failed to check availability: ${ex.getMessage}"))
          }
        } catch {
          case _: Exception => Future.successful(BadRequest(Json.obj("error" -> "Invalid date format. Use ISO format (e.g., 2024-01-15T10:00:00)")))
        }
      case _ =>
        Future.successful(BadRequest(Json.obj("error" -> "Missing required query parameters: roomId, startTime, endTime")))
    }
  }

  /**
   * GET /api/reservations/active
   * Get all active reservations
   */
  def getActiveReservations(): Action[AnyContent] = Action.async {
    reservationService.getAllActiveReservations().map { reservations =>
      val result = reservations.map { case (reservation, room) =>
        Json.obj(
          "reservationId" -> reservation.id,
          "roomId" -> room.id,
          "roomName" -> room.name,
          "location" -> room.location,
          "employeeName" -> reservation.employeeName,
          "employeeEmail" -> reservation.employeeEmail,
          "department" -> reservation.department,
          "purpose" -> reservation.purpose,
          "startTime" -> Json.toJson(reservation.startTime),
          "endTime" -> Json.toJson(reservation.endTime),
          "status" -> reservation.status
        )
      }
      Ok(Json.toJson(result))
    }.recover {
      case ex: Exception => InternalServerError(Json.obj("error" -> s"Failed to fetch active reservations: ${ex.getMessage}"))
    }
  }

  /**
   * GET /api/rooms
   * Get all available rooms
   */
  def getAllRooms(): Action[AnyContent] = Action.async {
    reservationService.getAllRooms().map { rooms =>
      Ok(Json.toJson(rooms))
    }.recover {
      case ex: Exception => InternalServerError(Json.obj("error" -> s"Failed to fetch rooms: ${ex.getMessage}"))
    }
  }

  /**
   * POST /api/reservations/process-reminders
   * Process pending reminders (typically called by a scheduled job)
   */
  def processReminders(): Action[AnyContent] = Action.async {
    reservationService.processReminders().map { count =>
      Ok(Json.obj("message" -> s"Processed $count reminders"))
    }.recover {
      case ex: Exception => InternalServerError(Json.obj("error" -> s"Failed to process reminders: ${ex.getMessage}"))
    }
  }

  /**
   * POST /api/reservations/process-auto-releases
   * Process auto-releases (typically called by a scheduled job)
   */
  def processAutoReleases(): Action[AnyContent] = Action.async {
    reservationService.processAutoReleases().map { count =>
      Ok(Json.obj("message" -> s"Processed $count auto-releases"))
    }.recover {
      case ex: Exception => InternalServerError(Json.obj("error" -> s"Failed to process auto-releases: ${ex.getMessage}"))
    }
  }
}

